"""The commit step: replaces the baseline with the run's components, less the failed ones."""

import polars as pl
import pytest

from merger.models.step_events import CommitEvent
from merger.steps import commit
from tests.merger.support import components, labelled, s3_read_error
from tests.mocks import MockS3Client, MockSmartOpen
from utils.aws import df_from_s3_parquet, df_to_s3_parquet

EVENT = CommitEvent(
    pipeline_date="2026-01-01", graph_date="2026-01-02", job_id="job-1", group_count=2
)


def run(failed: dict[int, list[str]]) -> commit.CommitResult:
    df_to_s3_parquet(
        components({"A": "AB", "C": "CD", "E": "E"}), EVENT.get_s3_uri("components")
    )
    for group in range(EVENT.group_count):
        frame = pl.DataFrame(
            {"component_id": failed.get(group, [])}, schema={"component_id": pl.Utf8}
        )
        df_to_s3_parquet(frame, EVENT.failed_components_s3_uri(group))
    return commit.handler(EVENT)


def test_replaces_the_baseline_with_the_runs_components() -> None:
    result = run({})

    baseline = df_from_s3_parquet(EVENT.baseline_s3_uri)
    assert labelled(baseline) == {"A": "AB", "C": "CD", "E": "E"}
    assert result.failed_component_count == 0


def test_leaves_failed_components_out_so_the_next_run_retries_them() -> None:
    result = run({0: ["A"], 1: ["E"]})

    baseline = df_from_s3_parquet(EVENT.baseline_s3_uri)
    assert labelled(baseline) == {"C": "CD"}
    assert result.failed_component_count == 2


def test_leaves_out_every_component_of_a_group_that_did_not_finish(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changed = pl.DataFrame(
        {"id": ["A", "B", "E"], "component_id": ["A", "A", "E"], "group": [0, 0, 1]}
    )
    df_to_s3_parquet(changed, EVENT.get_s3_uri("changed_components"))
    unfinished = EVENT.failed_components_s3_uri(1)

    def read(uri: str) -> pl.DataFrame:
        if uri == unfinished:
            raise s3_read_error("NoSuchKey")
        return df_from_s3_parquet(uri)

    monkeypatch.setattr(commit, "df_from_s3_parquet", read)
    result = run({})

    baseline = df_from_s3_parquet(EVENT.baseline_s3_uri)
    assert labelled(baseline) == {"A": "AB", "C": "CD"}
    assert result.failed_component_count == 1


def test_deletes_the_runs_copy_of_the_components() -> None:
    run({})

    deleted = [f"s3://{bucket}/{key}" for bucket, key in MockS3Client.deleted_keys]
    assert deleted == [EVENT.get_s3_uri("components")]


def test_a_run_with_no_groups_commits_its_components() -> None:
    no_groups = EVENT.model_copy(update={"group_count": 0})
    df_to_s3_parquet(components({"A": "A"}), no_groups.get_s3_uri("components"))

    commit.handler(no_groups)

    assert labelled(df_from_s3_parquet(no_groups.baseline_s3_uri)) == {"A": "A"}
    assert no_groups.baseline_s3_uri in MockSmartOpen.file_lookup
