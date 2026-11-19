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
        ),
        args(use_rest_api_table=True),
        args(pipeline_date="2026-09-30", graph_date="2026-09-30", match_only=True),
    ],
    ids=[
        "all local",
        "all the pipeline's",
        "the pipeline's table into local Elasticsearch",
        "matching only",
    ],
)
def test_allowed_runs(run: argparse.Namespace) -> None:
    assert environment_error(run) is None


@pytest.mark.parametrize(
    "run",
    [
        args(es_mode="public"),
        args(pipeline_date="2026-09-30", graph_date="2026-09-30"),
        args(
            pipeline_date="2026-09-30",
            graph_date="2026-09-30",
            use_rest_api_table=True,
        ),
    ],
    ids=[
        "the local table into the public cluster",
        "the local table into the pipeline's baseline",
        "local Elasticsearch with the pipeline's baseline",
    ],
)
def test_refused_runs(run: argparse.Namespace) -> None:
    assert environment_error(run) is not None
