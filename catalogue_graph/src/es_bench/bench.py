"""Write-throughput, scroll and load runs against one Elasticsearch target.

The same code runs from a laptop and from the VPC harness Lambda, so the
public and private paths are measured by identical requests.
"""

import math
import statistics
import time
from collections.abc import Callable, Iterable, Iterator, Sequence
from concurrent.futures import ThreadPoolExecutor
from typing import Any, cast

import elasticsearch
import structlog
from elasticsearch import Elasticsearch

from es_bench.models import (
    CellResult,
    EsTarget,
    LatencyStats,
    LoadSpec,
    PingSpec,
    RequestOutcome,
    SampleSpec,
    ScrollSpec,
    WriteCell,
    WriteSpec,
)
from utils import aws as aws_utils

logger = structlog.get_logger(__name__)

Doc = tuple[str, dict[str, Any]]


def resolve_target(target: EsTarget) -> EsTarget:
    """Fill host, port, scheme and key from Secrets Manager where secret names are given."""
    values = target.model_dump()
    if target.host_secret:
        values["host"] = aws_utils.get_secret(target.host_secret)
    if target.port_secret:
        values["port"] = int(aws_utils.get_secret(target.port_secret))
    if target.protocol_secret:
        values["scheme"] = aws_utils.get_secret(target.protocol_secret)
    if target.api_key_secret:
        values["api_key"] = aws_utils.get_secret(target.api_key_secret)
    resolved = EsTarget(**values)
    if resolved.host is None:
        raise ValueError(f"target {target.name} has neither host nor host_secret")
    return resolved


def get_client(target: EsTarget) -> Elasticsearch:
    resolved = resolve_target(target)
    return Elasticsearch(
        f"{resolved.scheme}://{resolved.host}:{resolved.port}",
        api_key=resolved.api_key,
        request_timeout=resolved.request_timeout,
        http_compress=resolved.http_compress,
    )


def percentile(sorted_values: Sequence[float], fraction: float) -> float:
    if not sorted_values:
        return 0.0
    index = min(
        len(sorted_values) - 1, max(0, math.ceil(fraction * len(sorted_values)) - 1)
    )
    return sorted_values[index]


def latency_stats(latencies_ms: Iterable[float]) -> LatencyStats:
    values = sorted(latencies_ms)
    if not values:
        return LatencyStats(count=0, p50_ms=0, p95_ms=0, p99_ms=0, max_ms=0, mean_ms=0)
    return LatencyStats(
        count=len(values),
        p50_ms=percentile(values, 0.50),
        p95_ms=percentile(values, 0.95),
        p99_ms=percentile(values, 0.99),
        max_ms=values[-1],
        mean_ms=statistics.fmean(values),
    )


def chunked(items: Sequence[Doc], size: int) -> Iterator[Sequence[Doc]]:
    for start in range(0, len(items), size):
        yield items[start : start + size]


def bulk_operations(
    index: str, docs: Sequence[Doc], id_prefix: str, version: int
) -> list[dict[str, Any]]:
    """Index actions shaped like ElasticIndexer's: external_gte with an explicit version."""
    operations: list[dict[str, Any]] = []
    for doc_id, source in docs:
        operations.append(
            {
                "index": {
                    "_index": index,
                    "_id": f"{id_prefix}{doc_id}",
                    "version": version,
                    "version_type": "external_gte",
                }
            }
        )
        operations.append(source)
    return operations


def _status_of(error: elasticsearch.ApiError | elasticsearch.TransportError) -> int:
    meta = getattr(error, "meta", None)
    status = getattr(meta, "status", None)
    return int(status) if status is not None else 0


def _count_item_outcomes(items: list[dict[str, Any]]) -> tuple[int, int]:
    item_429s = 0
    item_errors = 0
    for item in items:
        result = next(iter(item.values()))
        status = int(result.get("status", 200))
        error_type = result.get("error", {}).get("type")
        if status == 429:
            item_429s += 1
        elif status >= 400 and error_type != "version_conflict_engine_exception":
            item_errors += 1
    return item_429s, item_errors


def send_bulk(
    client: Elasticsearch,
    operations: list[dict[str, Any]],
    docs: int,
    max_retries: int,
) -> RequestOutcome:
    """One bulk round-trip, retried with backoff on 429 and transport errors."""
    retries = 0
    while True:
        started = time.perf_counter()
        try:
            response = client.bulk(operations=operations)
        except (elasticsearch.ApiError, elasticsearch.TransportError) as error:
            latency_ms = (time.perf_counter() - started) * 1000
            status = _status_of(error)
            retryable = status == 429 or isinstance(error, elasticsearch.TransportError)
            if retryable and retries < max_retries:
                retries += 1
                time.sleep(min(5.0, 0.25 * 2**retries))
                continue
            logger.warning(
                "bulk failed", status=status, retries=retries, error=str(error)
            )
            return RequestOutcome(
                latency_ms=latency_ms, docs=docs, status=status, retries=retries
            )
        latency_ms = (time.perf_counter() - started) * 1000
        items = cast(list[dict[str, Any]], response.body.get("items", []))
        item_429s, item_errors = _count_item_outcomes(items)
        return RequestOutcome(
            latency_ms=latency_ms,
            docs=docs,
            status=int(response.meta.status),
            retries=retries,
            item_429s=item_429s,
            item_errors=item_errors,
        )


def run_cell(
    client: Elasticsearch,
    spec: WriteSpec,
    cell: WriteCell,
    docs: Sequence[Doc],
    version: int,
) -> CellResult:
    """Write the docs in bulks of cell.bulk_size from cell.concurrency threads."""
    id_prefix = f"{spec.id_prefix}-b{cell.bulk_size}-c{cell.concurrency}-"
    batches = list(chunked(docs, cell.bulk_size))

    def work(batch: Sequence[Doc]) -> RequestOutcome:
        operations = bulk_operations(spec.index, batch, id_prefix, version)
        return send_bulk(client, operations, len(batch), spec.max_retries)

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=cell.concurrency) as pool:
        outcomes = list(pool.map(work, batches))
    wall = time.perf_counter() - started

    succeeded = [o for o in outcomes if 200 <= o.status < 300]
    written = sum(o.docs for o in succeeded)
    return CellResult(
        bulk_size=cell.bulk_size,
        concurrency=cell.concurrency,
        docs=written,
        wall_seconds=wall,
        docs_per_second=written / wall if wall > 0 else 0.0,
        requests=len(outcomes),
        latency=latency_stats(o.latency_ms for o in outcomes),
        http_429s=sum(1 for o in outcomes if o.status == 429),
        retries=sum(o.retries for o in outcomes),
        item_429s=sum(o.item_429s for o in outcomes),
        item_errors=sum(o.item_errors for o in outcomes),
        failed_requests=len(outcomes) - len(succeeded),
    )


def _pit_page(
    client: Elasticsearch,
    pit_id: str,
    keep_alive: str,
    size: int,
    slice_id: int,
    slice_max: int,
    search_after: list[Any] | None,
) -> list[dict[str, Any]]:
    body: dict[str, Any] = {
        "size": size,
        "pit": {"id": pit_id, "keep_alive": keep_alive},
        "sort": [{"_shard_doc": "asc"}],
        "query": {"match_all": {}},
    }
    if slice_max > 1:
        body["slice"] = {"id": slice_id, "max": slice_max}
    if search_after is not None:
        body["search_after"] = search_after
    return cast(list[dict[str, Any]], client.search(body=body)["hits"]["hits"])


def sample_docs(client: Elasticsearch, spec: SampleSpec) -> list[Doc]:
    """Pull real documents through a sliced point in time, the ingestor's read path."""
    pit_id = client.open_point_in_time(index=spec.index, keep_alive=spec.keep_alive)[
        "id"
    ]
    per_slice = math.ceil(spec.count / spec.slices)

    def read_slice(slice_id: int) -> list[Doc]:
        collected: list[Doc] = []
        search_after: list[Any] | None = None
        while len(collected) < per_slice:
            size = min(spec.page_size, per_slice - len(collected))
            hits = _pit_page(
                client,
                pit_id,
                spec.keep_alive,
                size,
                slice_id,
                spec.slices,
                search_after,
            )
            if not hits:
                break
            collected.extend((hit["_id"], hit["_source"]) for hit in hits)
            search_after = hits[-1]["sort"]
        return collected

    try:
        with ThreadPoolExecutor(max_workers=spec.slices) as pool:
            slices = list(pool.map(read_slice, range(spec.slices)))
    finally:
        client.close_point_in_time(id=pit_id)
    docs = [doc for part in slices for doc in part][: spec.count]
    logger.info("sampled documents", index=spec.index, count=len(docs))
    return docs


def write_matrix(
    source_client: Elasticsearch,
    target_client: Elasticsearch,
    sample: SampleSpec,
    spec: WriteSpec,
) -> dict[str, Any]:
    docs = sample_docs(source_client, sample)
    if not docs:
        raise RuntimeError(f"no documents sampled from {sample.index}")
    while len(docs) < spec.docs_per_cell:
        docs = docs + [
            (f"{i}-{doc_id}", source) for i, (doc_id, source) in enumerate(docs)
        ]
    docs = docs[: spec.docs_per_cell]
    version = spec.version or int(time.time())

    results = []
    for cell in spec.cells:
        result = run_cell(target_client, spec, cell, docs, version)
        logger.info("cell complete", **result.model_dump())
        results.append(result.model_dump())
    return {"version": version, "sampled": len(docs), "cells": results}


def scroll_index(client: Elasticsearch, spec: ScrollSpec) -> dict[str, Any]:
    """The snapshot generator's read path: classic scroll on a long keep-alive."""
    started = time.perf_counter()
    latencies: list[float] = []
    total = 0
    t0 = time.perf_counter()
    response = client.search(
        index=spec.index,
        scroll=spec.keep_alive,
        size=spec.page_size,
        query={"match_all": {}},
    )
    latencies.append((time.perf_counter() - t0) * 1000)
    scroll_id = response["_scroll_id"]
    hits = response["hits"]["hits"]
    try:
        while hits and (spec.max_docs is None or total < spec.max_docs):
            total += len(hits)
            t0 = time.perf_counter()
            response = client.scroll(scroll_id=scroll_id, scroll=spec.keep_alive)
            latencies.append((time.perf_counter() - t0) * 1000)
            scroll_id = response["_scroll_id"]
            hits = response["hits"]["hits"]
    finally:
        client.clear_scroll(scroll_id=scroll_id)
    wall = time.perf_counter() - started
    return {
        "docs": total,
        "wall_seconds": wall,
        "docs_per_second": total / wall if wall else 0.0,
        "latency": latency_stats(latencies).model_dump(),
    }


def load_slice(
    source_client: Elasticsearch,
    target_client: Elasticsearch,
    spec: LoadSpec,
    version: int,
) -> dict[str, Any]:
    """Copy one slice until done or the deadline, returning where to resume."""
    started = time.perf_counter()
    pit_id = source_client.open_point_in_time(
        index=spec.source_index, keep_alive=spec.keep_alive
    )["id"]
    search_after = spec.search_after
    outcomes: list[RequestOutcome] = []
    copied = 0
    exhausted = False

    def work(batch: Sequence[Doc]) -> RequestOutcome:
        operations = bulk_operations(spec.dest_index, batch, "", version)
        return send_bulk(target_client, operations, len(batch), 5)

    try:
        with ThreadPoolExecutor(max_workers=spec.concurrency) as pool:
            while time.perf_counter() - started < spec.deadline_seconds:
                hits = _pit_page(
                    source_client,
                    pit_id,
                    spec.keep_alive,
                    spec.page_size,
                    spec.slice_id,
                    spec.slice_max,
                    search_after,
                )
                if not hits:
                    exhausted = True
                    break
                docs: list[Doc] = [(hit["_id"], hit["_source"]) for hit in hits]
                page_outcomes = list(pool.map(work, chunked(docs, spec.bulk_size)))
                outcomes.extend(page_outcomes)
                copied += sum(o.docs for o in page_outcomes if 200 <= o.status < 300)
                search_after = hits[-1]["sort"]
    finally:
        source_client.close_point_in_time(id=pit_id)
    wall = time.perf_counter() - started
    return {
        "slice_id": spec.slice_id,
        "copied": copied,
        "exhausted": exhausted,
        "search_after": None if exhausted else search_after,
        "wall_seconds": wall,
        "docs_per_second": copied / wall if wall else 0.0,
        "requests": len(outcomes),
        "latency": latency_stats(o.latency_ms for o in outcomes).model_dump(),
        "http_429s": sum(1 for o in outcomes if o.status == 429),
        "failed_requests": sum(1 for o in outcomes if not 200 <= o.status < 300),
    }


def ping(client: Elasticsearch, spec: PingSpec) -> dict[str, Any]:
    """Round-trip latency of the cheapest request, for private against public."""
    latencies: list[float] = []
    info: dict[str, Any] = {}
    for _ in range(spec.requests):
        t0 = time.perf_counter()
        info = dict(client.info().body)
        latencies.append((time.perf_counter() - t0) * 1000)
    return {
        "version": info.get("version", {}),
        "cluster_name": info.get("cluster_name"),
        "latency": latency_stats(latencies).model_dump(),
    }


def timed(label: str, fn: Callable[[], dict[str, Any]]) -> dict[str, Any]:
    started = time.perf_counter()
    result = fn()
    result["elapsed_seconds"] = time.perf_counter() - started
    logger.info("run complete", label=label, elapsed_seconds=result["elapsed_seconds"])
    return result
