from inferrer.models import FindWorkEvent
from inferrer.steps import find_work
from models.events import PipelineIndexDates
from tests.mocks import MockElasticsearchClient, mock_es_secrets
from utils.elasticsearch import get_images_initial_index_name

PIPELINE_DATE = "2026-06-01"


def test_source_index_defaults_to_pipeline_date() -> None:
    event = FindWorkEvent(pipeline_date=PIPELINE_DATE)
    assert get_images_initial_index_name(event) == f"images-initial-{PIPELINE_DATE}"


def test_source_index_uses_index_dates_initial_when_set() -> None:
    event = FindWorkEvent(
        pipeline_date=PIPELINE_DATE,
        index_dates=PipelineIndexDates(initial="2026-06-15"),
    )
    assert get_images_initial_index_name(event) == "images-initial-2026-06-15"


def _seed_initial_images(ids: list[str]) -> None:
    index = f"images-initial-{PIPELINE_DATE}"
    for image_id in ids:
        MockElasticsearchClient.index(
            index=index,
            id=image_id,
            document={"modifiedTime": "2026-06-10T12:00:00Z"},
        )


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
