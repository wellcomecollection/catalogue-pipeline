"""The local run: which combinations of table, cluster and baseline it accepts."""

import argparse

import pytest

from merger.run_local import environment_error


def args(**overrides: object) -> argparse.Namespace:
    defaults = {
        "pipeline_date": "dev",
        "graph_date": "dev",
        "es_mode": "local",
        "use_rest_api_table": False,
        "match_only": False,
        "index_date_merged": None,
        "index_date_initial": None,
    }
    return argparse.Namespace(**{**defaults, **overrides})


@pytest.mark.parametrize(
    "run",
    [
        args(),
        args(
            pipeline_date="2026-09-30",
            graph_date="2026-09-30",
            es_mode="public",
            use_rest_api_table=True,
            index_date_merged="2026-11-17",
            index_date_initial="2026-11-17",
        ),
        args(pipeline_date="2026-09-30", graph_date="2026-09-30", match_only=True),
    ],
    ids=["all local", "all the pipeline's", "matching only"],
)
def test_runs_within_one_environment_are_allowed(run: argparse.Namespace) -> None:
    assert environment_error(run) is None


@pytest.mark.parametrize(
    "run",
    [
        args(
            pipeline_date="2026-09-30",
            graph_date="2026-09-30",
            use_rest_api_table=True,
        ),
        args(es_mode="public"),
        args(pipeline_date="2026-09-30", graph_date="2026-09-30"),
        args(
            pipeline_date="2026-09-30",
            graph_date="2026-09-30",
            es_mode="public",
            use_rest_api_table=True,
        ),
    ],
    ids=[
        "the pipeline's table into local Elasticsearch",
        "the local table into the pipeline's cluster",
        "the local table into the pipeline's baseline",
        "the pipeline's table into its own indices",
    ],
)
def test_runs_mixing_environments_are_refused(run: argparse.Namespace) -> None:
    assert environment_error(run) is not None
