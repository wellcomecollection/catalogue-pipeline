from typing import Any

from es_bench.bench import (
    _count_item_outcomes,
    bulk_operations,
    chunked,
    latency_stats,
    percentile,
)


def test_percentile_picks_nearest_rank() -> None:
    values = [float(v) for v in range(1, 101)]
    assert percentile(values, 0.50) == 50.0
    assert percentile(values, 0.95) == 95.0
    assert percentile(values, 0.99) == 99.0
    assert percentile([], 0.5) == 0.0


def test_latency_stats_summarises() -> None:
    stats = latency_stats([10.0, 20.0, 30.0, 40.0])
    assert stats.count == 4
    assert stats.p50_ms == 20.0
    assert stats.max_ms == 40.0
    assert stats.mean_ms == 25.0


def test_chunked_splits_without_loss() -> None:
    docs = [(str(i), {"i": i}) for i in range(7)]
    batches = list(chunked(docs, 3))
    assert [len(b) for b in batches] == [3, 3, 1]
    assert [d for b in batches for d in b] == docs


def test_bulk_operations_match_elastic_indexer_shape() -> None:
    ops = bulk_operations("works-x", [("abc", {"title": "t"})], "bench-", 7)
    assert ops == [
        {
            "index": {
                "_index": "works-x",
                "_id": "bench-abc",
                "version": 7,
                "version_type": "external_gte",
            }
        },
        {"title": "t"},
    ]


def test_item_outcomes_count_429s_and_real_errors_only() -> None:
    items: list[dict[str, Any]] = [
        {"index": {"status": 201}},
        {
            "index": {
                "status": 429,
                "error": {"type": "es_rejected_execution_exception"},
            }
        },
        {
            "index": {
                "status": 409,
                "error": {"type": "version_conflict_engine_exception"},
            }
        },
        {"index": {"status": 400, "error": {"type": "mapper_parsing_exception"}}},
    ]
    assert _count_item_outcomes(items) == (1, 1)
