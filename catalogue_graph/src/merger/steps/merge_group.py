"""Merges one group of a run's changed components and writes the merged works and
initial images to the batch merger's own indices."""

import typing
from collections.abc import Sequence
from datetime import UTC, datetime
from itertools import batched

import polars as pl
import structlog
from elasticsearch import Elasticsearch
from pyiceberg.table import Table as IcebergTable

import config
from core.document import Document
from core.sinks import ElasticsearchSink
from id_minter.iceberg import get_works_identified_table
from inferrer.models import InitialImage
from ingestor.models.merged.work import MergedWork
from merger.documents import read_documents, take_works
from merger.merge import MergedComponent, merge_component
from merger.models.step_events import MergeGroupEvent, MergeGroupResult
from merger.outputs import to_initial_image, to_merged_work
from utils.aws import df_from_s3_parquet, df_to_s3_parquet
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
    get_standard_index_name,
    version_from_datetime,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

# Components whose merged works and images go to Elasticsearch in one bulk write each.
WRITE_BATCH_SIZE = 1_000


def handler(
    event: MergeGroupEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
    use_rest_api_table: bool = True,
    es_client: Elasticsearch | None = None,
) -> MergeGroupResult:
    setup_logging(execution_context)

    changed = df_from_s3_parquet(event.get_s3_uri("changed_components")).filter(
        pl.col("group") == event.group
    )
    table = get_works_identified_table(use_rest_api_table)
    es_client = es_client or get_client(
        config.MERGER_ES_API_KEY_NAME, event.pipeline_date, es_mode
    )
    works_sink = ElasticsearchSink(es_client, works_index_name(event))
    images_sink = ElasticsearchSink(es_client, images_index_name(event))
    # Every document is versioned by the snapshot's commit time, so every group of a
    # run writes the same version and a run never overwrites a run at a later snapshot.
    merged_time = _snapshot_time(table, event.iceberg_snapshot_id)
    version = version_from_datetime(merged_time)

    documents = read_documents(table, event.iceberg_snapshot_id, set(changed["id"]))
    members = changed.group_by("component_id").agg("id").sort("component_id")
    merged = (
        (component_id, _merge(component_id, ids, documents))
        for component_id, ids in members.iter_rows()
    )

    failed: set[str] = set()
    component_count = work_count = image_count = superseded_count = 0
    for batch in batched(merged, WRITE_BATCH_SIZE):
        works: list[tuple[str, MergedWork]] = []
        images: list[tuple[str, InitialImage]] = []
        for component_id, component in batch:
            if component is None:
                failed.add(component_id)
                continue
            for work in component.works:
                works.append((component_id, to_merged_work(work, merged_time)))
            for image in component.images:
                initial_image = to_initial_image(image.image, image.source, merged_time)
                images.append((component_id, initial_image))
        works_result = works_sink.write(_documents(works, version))
        images_result = images_sink.write(_documents(images, version))
        for document, error in works_result.failed + images_result.failed:
            logger.warning(
                "Document refused",
                component=document.source_id,
                document=document.target_id,
                error=error,
            )
            failed.add(document.source_id)
        component_count += len(batch)
        work_count += len(works_result.accepted)
        image_count += len(images_result.accepted)
        superseded_count += len(works_result.superseded) + len(images_result.superseded)

    df_to_s3_parquet(
        pl.DataFrame(
            {"component_id": sorted(failed)}, schema={"component_id": pl.Utf8}
        ),
        event.failed_components_s3_uri(event.group),
    )
    logger.info(
        "Merged group",
        group=event.group,
        components=component_count,
        works=work_count,
        images=image_count,
        superseded=superseded_count,
        failed_components=len(failed),
    )
    return MergeGroupResult(
        group=event.group,
        component_count=component_count,
        work_count=work_count,
        image_count=image_count,
        superseded_count=superseded_count,
        failed_component_count=len(failed),
    )


def works_index_name(event: MergeGroupEvent) -> str:
    index_date = event.index_dates.merged or event.pipeline_date
    return get_standard_index_name(config.MERGER_WORKS_INDEX_NAME, index_date)


def images_index_name(event: MergeGroupEvent) -> str:
    index_date = event.index_dates.initial or event.pipeline_date
    return get_standard_index_name(config.MERGER_IMAGES_INDEX_NAME, index_date)


def _snapshot_time(table: IcebergTable, snapshot_id: int) -> datetime:
    snapshot = table.snapshot_by_id(snapshot_id)
    if snapshot is None:
        raise ValueError(f"Snapshot {snapshot_id} not found")
    return datetime.fromtimestamp(snapshot.timestamp_ms / 1000, UTC)


def _merge(
    component_id: str, ids: list[str], documents: dict[str, str]
) -> MergedComponent | None:
    """The merged component, or None when it could not be read or merged, so one bad
    component does not stop the rest of the group."""
    try:
        return merge_component(take_works(documents, ids))
    except Exception:
        logger.exception("Component failed to merge", component=component_id, ids=ids)
        return None


def _documents(
    models: Sequence[tuple[str, MergedWork | InitialImage]], version: int
) -> list[Document]:
    """Each merged work or image as a document, read under its component, so a document
    Elasticsearch refuses can be traced back to it."""
    return [
        Document(
            source_id=component_id,
            target_id=model.state.canonical_id,
            body=model.model_dump(exclude_none=True, mode="json"),
            version=version,
        )
        for component_id, model in models
    ]


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="batch_merger_group",
    )
    return handler(MergeGroupEvent.model_validate(event), execution_context).model_dump(
        mode="json"
    )
