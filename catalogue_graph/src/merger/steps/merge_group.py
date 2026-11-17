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
from merger.merge import merge_component
from merger.models.step_events import MergeGroupEvent, MergeGroupResult
from merger.outputs import to_initial_image, to_merged_work
from utils.aws import df_from_s3_parquet, df_to_s3_parquet
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
    get_images_initial_index_name,
    get_merged_index_name,
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

    table = get_works_identified_table(use_rest_api_table)
    merged_time = _snapshot_time(table, event.iceberg_snapshot_id)
    changed = df_from_s3_parquet(event.get_s3_uri("changed_components")).filter(
        pl.col("group") == event.group
    )
    identified = read_documents(table, event.iceberg_snapshot_id, set(changed["id"]))
    es_client = es_client or get_client(
        config.MERGER_ES_API_KEY_NAME, event.pipeline_date, es_mode
    )
    works_sink = ElasticsearchSink(es_client, get_merged_index_name(event))
    images_sink = ElasticsearchSink(es_client, get_images_initial_index_name(event))

    members = changed.group_by("component_id").agg("id").sort("component_id")
    failed: set[str] = set()
    for batch in batched(members.iter_rows(), WRITE_BATCH_SIZE):
        works: dict[str, list[Document]] = {}
        images: dict[str, list[Document]] = {}
        for component_id, ids in batch:
            # An error here is confined to this component, which the next run retries.
            try:
                works[component_id], images[component_id] = _merge(
                    ids, identified, merged_time
                )
            except Exception:
                logger.exception(
                    "Component failed to merge", component=component_id, ids=ids
                )
                failed.add(component_id)
        failed |= _write(works_sink, works) | _write(images_sink, images)

    df_to_s3_parquet(
        pl.DataFrame(
            {"component_id": sorted(failed)}, schema={"component_id": pl.Utf8}
        ),
        event.failed_components_s3_uri(event.group),
    )
    result = MergeGroupResult(
        group=event.group,
        component_count=members.height,
        failed_component_count=len(failed),
    )
    logger.info("Merged group", **result.model_dump())
    return result


def _snapshot_time(table: IcebergTable, snapshot_id: int) -> datetime:
    snapshot = table.snapshot_by_id(snapshot_id)
    if snapshot is None:
        raise ValueError(f"Snapshot {snapshot_id} not found")
    return datetime.fromtimestamp(snapshot.timestamp_ms / 1000, UTC)


def _merge(
    ids: list[str], identified: dict[str, str], merged_time: datetime
) -> tuple[list[Document], list[Document]]:
    """The work and image documents of one merged component."""
    component = merge_component(take_works(identified, ids))
    works = [to_merged_work(work, merged_time) for work in component.works]
    images = [
        to_initial_image(i.image, i.source, merged_time) for i in component.images
    ]
    return _documents(works, merged_time), _documents(images, merged_time)


def _documents(
    models: Sequence[MergedWork | InitialImage], merged_time: datetime
) -> list[Document]:
    """Each merged work or image as a document, versioned by the snapshot's commit time,
    so every group of a run writes the same version and a run never overwrites a run at
    a later snapshot."""
    version = version_from_datetime(merged_time)
    return [
        Document(
            source_id=model.state.canonical_id,
            target_id=model.state.canonical_id,
            body=model.model_dump(exclude_none=True, mode="json"),
            version=version,
        )
        for model in models
    ]


def _write(sink: ElasticsearchSink, documents: dict[str, list[Document]]) -> set[str]:
    """Writes the documents of a batch of components, and returns the components with a
    document Elasticsearch refused."""
    component_of = {d.target_id: c for c, docs in documents.items() for d in docs}
    result = sink.write([d for docs in documents.values() for d in docs])
    refused = {component_of[document.target_id] for document, _ in result.failed}
    if refused:
        document, error = result.failed[0]
        logger.warning(
            "Documents refused",
            index=sink.index_name,
            documents=len(result.failed),
            components=len(refused),
            example_document=document.target_id,
            example_error=error,
        )
    return refused


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="batch_merger_group",
    )
    return handler(MergeGroupEvent.model_validate(event), execution_context).model_dump(
        mode="json"
    )
