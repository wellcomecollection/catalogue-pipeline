import argparse
import sys

import pytest

import config
from ingestor.steps import ingestor_deletions, ingestor_indexer
from models.events import BulkLoaderEvent
from pit_opener import lambda_handler as pit_opener_lambda
from tests.mocks import MockS3Client, MockSecretsManagerClient, mock_es_secrets
from utils.argparse import add_pipeline_event_args, validate_es_mode_for_writes
from utils.reporting import IncrementalGraphRemoverReport, LoaderReport


def test_ingestor_indexer_local_defaults_dev_local(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    MockSecretsManagerClient.calls = []
    parser = argparse.ArgumentParser()
    monkeypatch.setattr(sys, "argv", ["prog", "--ingestor-type", "concepts"])

    ingestor_indexer.local_handler(parser)

    bucket = config.CATALOGUE_GRAPH_S3_BUCKETS["dev"]
    prefix = f"{config.INGESTOR_S3_PREFIX}_concepts/dev/dev/dev"

    assert MockS3Client.list_objects_v2_calls == [(bucket, prefix)]
    assert MockSecretsManagerClient.calls == []


def test_lambda_defaults_use_prod_private_es() -> None:
    pipeline_date = "2025-01-01"
    MockSecretsManagerClient.calls = []
    mock_es_secrets("graph_extractor", pipeline_date)
    pit_opener_lambda({"pipeline_date": pipeline_date}, None)

    expected_prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"
    assert set(MockSecretsManagerClient.calls) == {
        f"{expected_prefix}/private_host",
        f"{expected_prefix}/port",
        f"{expected_prefix}/protocol",
        f"{expected_prefix}/graph_extractor/api_key",
    }


def test_ingestor_deletions_rejects_dev_public_es(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--environment",
            "dev",
            "--es-mode",
            "public",
        ],
    )

    with pytest.raises(SystemExit):
        ingestor_deletions.local_handler()


def test_ingestor_indexer_rejects_dev_public_es(
    monkeypatch: pytest.MonkeyPatch,
):
    parser = argparse.ArgumentParser()
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--ingestor-type",
            "concepts",
            "--environment",
            "dev",
            "--es-mode",
            "public",
        ],
    )

    with pytest.raises(SystemExit):
        ingestor_indexer.local_handler(parser)


def test_s3_bucket_selection_by_environment() -> None:
    prod_event = BulkLoaderEvent(
        pipeline_date="2025-01-01",
        transformer_type="loc_concepts",
        entity_type="nodes",
        environment="prod",
    )
    dev_event = BulkLoaderEvent(
        pipeline_date="2025-01-01",
        transformer_type="loc_concepts",
        entity_type="nodes",
        environment="dev",
    )

    assert prod_event.get_s3_uri().startswith(
        f"s3://{config.CATALOGUE_GRAPH_S3_BUCKETS['prod']}/"
    )
    assert dev_event.get_s3_uri().startswith(
        f"s3://{config.CATALOGUE_GRAPH_S3_BUCKETS['dev']}/"
    )


def test_es_mode_validation_allows_expected_pairs() -> None:
    parser = argparse.ArgumentParser()
    add_pipeline_event_args(parser, {"environment", "es_mode"})

    prod_args = parser.parse_args(["--environment", "prod", "--es-mode", "public"])
    validate_es_mode_for_writes(parser, prod_args)

    dev_args = parser.parse_args(["--environment", "dev", "--es-mode", "local"])
    validate_es_mode_for_writes(parser, dev_args)


def test_metric_namespace_for_graph_reports() -> None:
    prod_event = IncrementalGraphRemoverReport(
        pipeline_date="2025-01-01",
        transformer_type="catalogue_concepts",
        entity_type="nodes",
        environment="prod",
        deleted_count=1,
    )
    dev_event = IncrementalGraphRemoverReport(
        pipeline_date="2025-01-01",
        transformer_type="catalogue_concepts",
        entity_type="nodes",
        environment="dev",
        deleted_count=1,
    )

    assert prod_event.metric_namespace == "catalogue_graph_pipeline"
    assert dev_event.metric_namespace == "catalogue_graph_pipeline_dev"


def test_metric_namespace_for_ingestor_reports() -> None:
    prod_report = LoaderReport(
        pipeline_date="2025-01-01",
        ingestor_type="concepts",
        job_id="20250101T0101",
        environment="prod",
        record_count=1,
        total_file_size=1,
    )
    dev_report = LoaderReport(
        pipeline_date="2025-01-01",
        ingestor_type="concepts",
        job_id="20250101T0101",
        environment="dev",
        record_count=1,
        total_file_size=1,
    )

    assert prod_report.metric_namespace == "catalogue_graph_pipeline"
    assert dev_report.metric_namespace == "catalogue_graph_pipeline_dev"
