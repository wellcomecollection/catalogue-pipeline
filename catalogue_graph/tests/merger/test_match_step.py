"""The match step: what it reads, writes and reports on each run."""

import json
from pathlib import Path

import polars as pl
import pytest
from pyiceberg.catalog.sql import SqlCatalog

from id_minter.schemata import WORKS_IDENTIFIED_ICEBERG_SCHEMA
from merger import components as storage
from merger.__main__ import commit_components
from merger.models.step_events import MatchEvent, MergeGroupEvent
from merger.steps import match
from tests.merger.support import (
    components,
    iceberg_table,
    labelled,
    s3_read_error,
    works,
)
from tests.mocks import MockS3Client, MockSmartOpen
from utils.aws import df_from_s3_parquet, df_to_s3_parquet

EVENT = MatchEvent(pipeline_date="2026-01-01", graph_date="2026-01-02", job_id="job-1")
GRAPH = {"A": "B", "B": "", "C": "D", "D": "", "E": ""}


def read_like_s3(uri: str) -> pl.DataFrame:
    """The mock raises KeyError for a missing file; S3 reports a missing key."""
    if uri not in MockSmartOpen.file_lookup:
        raise s3_read_error("NoSuchKey")
    return df_from_s3_parquet(uri)


def run(
    monkeypatch: pytest.MonkeyPatch, event: MatchEvent = EVENT, snapshot_id: int = 7
) -> match.MatchResult:
    table = iceberg_table(works(GRAPH), snapshot_id)
    monkeypatch.setattr(match, "get_works_identified_table", lambda use_rest: table)
    monkeypatch.setattr(storage, "df_from_s3_parquet", read_like_s3)
    return match.handler(event)


def written(uri: str) -> dict[str, str]:
    return labelled(df_from_s3_parquet(uri))


def test_first_run_reports_every_work(monkeypatch: pytest.MonkeyPatch) -> None:
    result = run(monkeypatch)

    assert (result.work_count, result.changed_work_count) == (5, 5)
    assert result.changed_component_count == 3
    assert written(result.changed_components_s3_uri) == {"A": "AB", "C": "CD", "E": "E"}


def test_later_run_reports_only_changed_works(monkeypatch: pytest.MonkeyPatch) -> None:
    MockSmartOpen.mock_s3_parquet_file(
        EVENT.components_s3_uri, components({"A": "AB", "C": "C", "D": "D", "E": "E"})
    )

    result = run(monkeypatch)

    assert (result.changed_work_count, result.changed_component_count) == (2, 1)
    assert written(result.changed_components_s3_uri) == {"C": "CD"}


def test_writes_the_whole_graph_for_the_job_and_leaves_the_baseline(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Written rather than mocked, so the test can read it after the step has.
    df_to_s3_parquet(components({"Z": "Z"}), EVENT.components_s3_uri)

    result = run(monkeypatch)

    assert written(result.job_components_s3_uri) == {"A": "AB", "C": "CD", "E": "E"}
    assert written(EVENT.components_s3_uri) == {"Z": "Z"}


def test_commit_replaces_the_baseline_and_deletes_the_jobs_copy(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    MockSmartOpen.mock_s3_parquet_file(EVENT.components_s3_uri, components({"Z": "Z"}))

    result = run(monkeypatch)
    commit_components(result)

    assert written(EVENT.components_s3_uri) == {"A": "AB", "C": "CD", "E": "E"}
    deleted = [f"s3://{bucket}/{key}" for bucket, key in MockS3Client.deleted_keys]
    assert deleted == [result.job_components_s3_uri]
    assert written(result.changed_components_s3_uri) != {}


def test_numbers_the_groups_of_changed_components(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        match,
        "assign_groups",
        lambda changed: storage.assign_groups(changed, group_size=2),
    )

    result = run(monkeypatch)

    changed = df_from_s3_parquet(result.changed_components_s3_uri)
    groups = changed.group_by("group").agg(pl.col("id").sort().str.join(""))
    assert dict(groups.sort("group").iter_rows()) == {0: "AB", 1: "CD", 2: "E"}
    assert result.group_count == 3


def test_nothing_changed_gives_no_groups(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    MockSmartOpen.mock_s3_parquet_file(
        EVENT.components_s3_uri, components({"A": "AB", "C": "CD", "E": "E"})
    )

    result = run(monkeypatch)

    assert result.changed_work_count == 0
    assert result.group_count == 0


def test_reads_the_current_snapshot_unless_one_is_given(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    assert run(monkeypatch, snapshot_id=7).iceberg_snapshot_id == 7

    pinned = EVENT.model_copy(update={"iceberg_snapshot_id": 5})
    assert run(monkeypatch, pinned, snapshot_id=7).iceberg_snapshot_id == 5


@pytest.mark.parametrize("appended", [False, True])
def test_an_empty_table_reports_nothing_changed(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path, appended: bool
) -> None:
    """A new pipeline's table has no snapshot; an append of no rows gives one with no files."""
    catalog = SqlCatalog(
        "test", uri=f"sqlite:///{tmp_path}/catalog.db", warehouse=f"file://{tmp_path}"
    )
    catalog.create_namespace("ns")
    table = catalog.create_table("ns.works", schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA)
    if appended:
        table.append(table.schema().as_arrow().empty_table())
    monkeypatch.setattr(match, "get_works_identified_table", lambda use_rest: table)
    monkeypatch.setattr(storage, "df_from_s3_parquet", read_like_s3)

    result = match.handler(EVENT)

    assert (result.iceberg_snapshot_id is None) == (not appended)
    assert (result.work_count, result.changed_work_count) == (0, 0)
    assert result.changed_component_count == 0
    assert written(result.changed_components_s3_uri) == {}
    assert df_from_s3_parquet(result.job_components_s3_uri).height == 0
    assert result.group_count == 0


def test_the_baseline_is_shared_by_jobs_and_the_changed_file_is_not() -> None:
    other_job = EVENT.model_copy(update={"job_id": "job-2"})

    assert EVENT.components_s3_uri == other_job.components_s3_uri
    assert EVENT.components_s3_uri.endswith(
        "/graph-2026-01-02/pipeline-2026-01-01/merger/components.parquet"
    )
    assert EVENT.get_s3_uri("changed_components").endswith(
        "/graph-2026-01-02/pipeline-2026-01-01/merger/full/job-job-1/changed_components.parquet"
    )
    assert EVENT.get_s3_uri("x") != other_job.get_s3_uri("x")


def test_the_snapshot_id_survives_numbers_read_as_doubles() -> None:
    """Step Functions' JSONata reads JSON numbers as doubles, which round a typical
    Iceberg snapshot id."""
    snapshot_id = 9085339146097452536
    result = match.MatchResult(
        job_id="job-1",
        iceberg_snapshot_id=snapshot_id,
        work_count=0,
        changed_work_count=0,
        changed_component_count=0,
        group_count=0,
        changed_components_s3_uri="",
        job_components_s3_uri="",
        components_s3_uri="",
    )
    event = MergeGroupEvent(
        pipeline_date="2026-01-01",
        graph_date="2026-01-02",
        job_id="job-1",
        iceberg_snapshot_id=snapshot_id,
        group=0,
    )

    as_doubles = json.loads(result.model_dump_json(), parse_int=float)
    assert as_doubles["iceberg_snapshot_id"] == str(snapshot_id)
    passed_on = json.loads(event.model_dump_json(), parse_int=float)
    assert MergeGroupEvent.model_validate(passed_on).iceberg_snapshot_id == snapshot_id
