from inferrer.models import FindWorkEvent
from inferrer.steps import find_work
from tests.mocks import MockElasticsearchClient, mock_es_secrets

PIPELINE_DATE = "2026-06-01"


def _seed_initial_images(ids: list[str]) -> None:
    index = f"images-initial-{PIPELINE_DATE}"
    for image_id in ids:
        MockElasticsearchClient.index(
            index=index,
            id=image_id,
            document={"modifiedTime": "2026-06-10T12:00:00Z"},
        )


def test_partition_chunks_ids() -> None:
    assert find_work.partition(["a", "b", "c", "d", "e"], 2) == [
        ["a", "b"],
        ["c", "d"],
        ["e"],
    ]


def test_handler_returns_partitioned_work() -> None:
    mock_es_secrets("inferrer", PIPELINE_DATE)
    _seed_initial_images(["a", "b", "c"])

    event = FindWorkEvent(pipeline_date=PIPELINE_DATE, partition_size=2)
    result = find_work.handler(event, es_mode="private")

    assert len(result.partitions) == 2
    all_ids = [image_id for p in result.partitions for image_id in (p.ids or [])]
    assert sorted(all_ids) == ["a", "b", "c"]
    assert all(p.pipeline_date == PIPELINE_DATE for p in result.partitions)


def test_handler_builds_window_query_on_modified_time() -> None:
    mock_es_secrets("inferrer", PIPELINE_DATE)
    _seed_initial_images(["a"])

    event = FindWorkEvent.model_validate(
        {
            "pipeline_date": PIPELINE_DATE,
            "window": {"end_time": "2026-06-10T12:00:00Z"},
        }
    )
    find_work.handler(event, es_mode="private")

    # The discovery query must range over the top-level `modifiedTime` field.
    assert MockElasticsearchClient.queries[-1] == {
        "range": {
            "modifiedTime": {
                "gte": "2026-06-10T11:45:00+00:00",
                "lte": "2026-06-10T12:00:00+00:00",
            }
        }
    }
