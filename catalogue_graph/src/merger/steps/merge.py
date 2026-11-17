#!/usr/bin/env python
"""Merges one group of a run's changed components and writes the merged works and
initial images to the batch merger's own indices."""

import typing
from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from itertools import batched

import polars as pl
import structlog
from elasticsearch import Elasticsearch

import config
from core.document import Document
from core.sinks import ElasticsearchSink
from id_minter.iceberg import get_works_identified_table
from merger.documents import fetch_components
from merger.merge import merge_component
from merger.merge_result import MergerOutcome
from merger.models.step_events import MergeGroupEvent, MergeGroupResult
from models.pipeline.identified.work import IdentifiedWork
from utils.aws import df_from_s3_parquet
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


@dataclass
class _Counts:
    components: int = 0
    works: int = 0
    merged_works: int = 0
    images: int = 0


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
    # Every document is versioned by the snapshot time, so a run never overwrites
    # the output of a run at a later snapshot.
    merged_time = event.merged_time
    version = version_from_datetime(merged_time)

    counts = _Counts()
    outcomes = _merge(
        fetch_components(table, event.iceberg_snapshot_id, changed), counts
    )
    failed: list[tuple[Document, typing.Any]] = []
    superseded = 0
    for batch in batched(outcomes, WRITE_BATCH_SIZE):
        works = [
            _document(work.state.canonical_id, work, version)
            for outcome in batch
            for work in outcome.merged_works(merged_time)
        ]
        images = [
            _document(image.state.canonical_id, image, version)
            for outcome in batch
            for image in outcome.initial_images(merged_time)
        ]
        for sink, documents in ((works_sink, works), (images_sink, images)):
            if documents:
                result = sink.write(documents)
                failed += result.failed
                superseded += len(result.superseded)

    logger.info(
        "Merged group",
        group=event.group,
        components=counts.components,
        works=counts.works,
        merged_works=counts.merged_works,
        images=counts.images,
        superseded=superseded,
        failed=len(failed),
    )
    if failed:
        document, error = failed[0]
        raise RuntimeError(
            f"{len(failed)} documents failed to index, first {document.target_id}: {error}"
        )

    return MergeGroupResult(
        group=event.group,
        component_count=counts.components,
        work_count=counts.works,
        merged_work_count=counts.merged_works,
        image_count=counts.images,
    )


def works_index_name(event: MergeGroupEvent) -> str:
    index_date = event.index_dates.merged or event.pipeline_date
    return get_standard_index_name(config.MERGER_WORKS_INDEX_NAME, index_date)


def images_index_name(event: MergeGroupEvent) -> str:
    index_date = event.index_dates.initial or event.pipeline_date
    return get_standard_index_name(config.MERGER_IMAGES_INDEX_NAME, index_date)


def _merge(
    components: Iterable[tuple[list[str], list[IdentifiedWork]]], counts: _Counts
) -> Iterator[MergerOutcome]:
    for ids, works in components:
        outcome = merge_component(ids, works)
        counts.components += 1
        counts.works += len(works)
        counts.merged_works += len(outcome.works)
        counts.images += len(outcome.images)
        yield outcome


def _document(canonical_id: str, model: typing.Any, version: int) -> Document:
    body = model.model_dump(exclude_none=True, mode="json")
    return Document(
        source_id=canonical_id, target_id=canonical_id, body=body, version=version
    )


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="merger",
    )
    return handler(MergeGroupEvent.model_validate(event), execution_context).model_dump(
        mode="json"
    )
