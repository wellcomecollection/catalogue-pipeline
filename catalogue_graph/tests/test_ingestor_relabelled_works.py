from typing import Any
from unittest.mock import MagicMock, patch

import pyarrow as pa
import pyarrow.parquet as pq

from ingestor.models.step_events import IngestorRelabelledWorksLambdaEvent
from ingestor.steps.ingestor_relabelled_works import handler
from tests.mocks import MockS3Client, MockSmartOpen

BUCKET = "wellcomecollection-catalogue-graph"
PREFIX = "graph-dev/pipeline-dev/ingestor_concepts/index-dev/full"


def _mock_job(job_name: str, concepts: dict[str, str]) -> str:
    """Write one ingest job's concept documents, keyed by id with their display label."""
    key = f"{PREFIX}/{job_name}/00000000-00000010.parquet"

    table = pa.table(
        {
            "query": pa.array([{"id": i} for i in concepts]),
            "display": pa.array(
                [{"displayLabel": label} for label in concepts.values()]
            ),
        }
    )
    buffer = pa.BufferOutputStream()
    pq.write_table(table, buffer)
    MockSmartOpen.mock_s3_file(f"s3://{BUCKET}/{key}", buffer.getvalue().to_pybytes())

    MockS3Client.add_list_objects_response(
        BUCKET, f"{PREFIX}/{job_name}/", [{"Key": key}]
    )
    return key


def _mock_jobs(jobs: dict[str, dict[str, str]]) -> None:
    keys = [_mock_job(name, concepts) for name, concepts in jobs.items()]
    MockS3Client.add_list_objects_response(
        BUCKET, f"{PREFIX}/", [{"Key": key} for key in keys]
    )


def _run(works_by_concept: dict[str, set[str]] | None = None, **overrides: Any) -> Any:
    neptune_client = MagicMock()
    neptune_client.get_source_node_ids.return_value = works_by_concept or {}

    event = IngestorRelabelledWorksLambdaEvent(
        pipeline_date="dev", graph_date="dev", **overrides
    )
    with patch(
        "ingestor.steps.ingestor_relabelled_works.NeptuneClient",
        return_value=neptune_client,
    ):
        return handler(event), neptune_client


def test_returns_the_works_of_a_relabelled_concept() -> None:
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "Old label", "concept2": "Unchanged"},
            "job-20250102T0000": {"concept1": "New label", "concept2": "Unchanged"},
        }
    )

    result, neptune_client = _run({"concept1": {"work0001", "work0002"}})

    assert result.concept_count == 1
    assert result.work_ids == ["work0001", "work0002"]
    assert result.work_count == 2
    assert result.over_limit is False
    neptune_client.get_source_node_ids.assert_called_once_with(
        ["concept1"],
        edge_label="HAS_CONCEPT",
        node_label="Concept",
        source_label="Work",
    )


def test_returns_nothing_when_no_label_changed() -> None:
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "Unchanged"},
            "job-20250102T0000": {"concept1": "Unchanged"},
        }
    )

    result, neptune_client = _run()

    assert result.concept_count == 0
    assert result.work_ids == []
    neptune_client.get_source_node_ids.assert_not_called()


def test_ignores_a_concept_which_is_new_in_the_latest_job() -> None:
    # The work which introduced the concept was ingested with it, so it is not stale.
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "Unchanged"},
            "job-20250102T0000": {"concept1": "Unchanged", "concept2": "Brand new"},
        }
    )

    result, _ = _run()

    assert result.concept_count == 0


def test_ignores_a_concept_which_is_gone_from_the_latest_job() -> None:
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "Unchanged", "concept2": "Removed"},
            "job-20250102T0000": {"concept1": "Unchanged"},
        }
    )

    result, _ = _run()

    assert result.concept_count == 0


def test_compares_the_two_most_recent_jobs() -> None:
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "First label"},
            "job-20250102T0000": {"concept1": "Second label"},
            "job-20250103T0000": {"concept1": "Second label"},
        }
    )

    result, _ = _run()

    assert result.concept_count == 0


def test_returns_nothing_when_there_is_no_earlier_job() -> None:
    _mock_jobs({"job-20250101T0000": {"concept1": "Only label"}})

    result, neptune_client = _run()

    assert result.concept_count == 0
    neptune_client.get_source_node_ids.assert_not_called()


def test_reports_and_stops_when_too_many_concepts_changed() -> None:
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "Old one", "concept2": "Old two"},
            "job-20250102T0000": {"concept1": "New one", "concept2": "New two"},
        }
    )

    result, neptune_client = _run(max_work_ids=1)

    assert result.over_limit is True
    assert result.concept_count == 2
    assert result.work_ids == []
    assert result.work_count == 0
    neptune_client.get_source_node_ids.assert_not_called()


def test_reports_and_stops_when_too_many_works_would_be_refreshed() -> None:
    _mock_jobs(
        {
            "job-20250101T0000": {"concept1": "Old label"},
            "job-20250102T0000": {"concept1": "New label"},
        }
    )

    result, _ = _run({"concept1": {"work0001", "work0002"}}, max_work_ids=1)

    assert result.over_limit is True
    assert result.concept_count == 1
    assert result.work_ids == []
