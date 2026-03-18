"""Integration tests for the id_minter step.

These tests exercise the full execute() / handler() path with a database
connection (via Docker Compose MySQL), while mocking Elasticsearch reads/writes.

Run with:
    uv run pytest tests/id_minter/test_id_minter.py -v

Skip with:
    uv run pytest --skip-db
"""

from __future__ import annotations

import json

import pymysql
import pymysql.connections
import pytest

from id_minter.config import IdMinterConfig, RDSClientConfig
from id_minter.models.identifier import SourceId
from id_minter.models.step_events import (
    StepFunctionMintingRequest,
)
from id_minter.resolvers.minting_resolver import MintingResolver
from id_minter.steps.id_minter import (
    IdMinterRuntime,
    execute,
    handler,
)
from tests.id_minter.conftest import (
    get_canonical_status,
    make_source_identifier,
    make_work_doc,
    seed_free_ids,
    seed_identifier,
    stub_transformer_source,
)
from tests.mocks import (
    MockCloudwatchClient,
    MockElasticsearchClient,
    MockSmartOpen,
    MockSNSClient,
)
from utils.models.manifests import StepManifest

pytestmark = pytest.mark.database

TEST_SNS_TOPIC_ARN = "arn:aws:sns:eu-west-1:123456789:test-topic"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _build_runtime(
    conn: pymysql.connections.Connection,
    downstream_sns_topic_arn: str | None = None,
) -> IdMinterRuntime:
    """Build a runtime with a MintingResolver backed by the DB connection."""
    resolver = MintingResolver.from_connection(conn)
    config = IdMinterConfig(
        rds_client=RDSClientConfig(password="id_minter"),
        apply_migrations=False,
        downstream_sns_topic_arn=downstream_sns_topic_arn,
    )
    return IdMinterRuntime(
        config=config,
        resolver=resolver,
        source_es_mode="local",
        target_es_mode="local",
    )


def _read_ndjson(uri: str) -> list[dict]:
    """Read NDJSON lines from a MockSmartOpen-backed S3 URI."""
    path = MockSmartOpen.file_lookup[uri]
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def _read_batch_ids(manifest: StepManifest) -> list[str]:
    """Extract all canonical IDs from the success batch file."""
    loc = manifest.successes.batch_file_location
    uri = f"s3://{loc.bucket}/{loc.key}"
    lines = _read_ndjson(uri)
    ids: list[str] = []
    for line in lines:
        ids.extend(line["canonicalIds"])
    return ids


def _read_failure_details(manifest: StepManifest) -> list[dict]:
    """Read failure detail records from the failure batch file."""
    assert manifest.failures is not None
    loc = manifest.failures.error_file_location
    uri = f"s3://{loc.bucket}/{loc.key}"
    return _read_ndjson(uri)


# ---------------------------------------------------------------------------
# Tests: execute() with real resolver
# ---------------------------------------------------------------------------


class TestExecuteWithRealResolver:
    """End-to-end tests for execute() using a MintingResolver + MySQL."""

    def test_mints_new_ids_for_work(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """A new work document gets a canonical ID minted from the free pool."""
        seed_free_ids(ids_db, ["mint0001"])

        si = make_source_identifier("Work", "sierra-system-number", "b1000001")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b1000001]"],
            job_id="integration-test-1",
        )

        with stub_transformer_source([doc]):
            response = execute(request, runtime=runtime)

        assert isinstance(response, StepManifest)
        assert response.job_id == "integration-test-1"
        assert response.failures is None
        assert response.successes.count == 1
        assert _read_batch_ids(response) == ["mint0001"]

        # Verify the ID was actually assigned in the database
        assert get_canonical_status(ids_db, "mint0001") == "assigned"

    def test_mints_ids_for_multiple_works(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """Multiple new work documents each get unique canonical IDs."""
        seed_free_ids(ids_db, ["multi001", "multi002", "multi003"])

        docs = [
            make_work_doc(
                make_source_identifier("Work", "sierra-system-number", f"b{i}")
            )
            for i in range(1, 4)
        ]

        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=[
                f"Work[sierra-system-number/b{i}]" for i in range(1, 4)
            ],
            job_id="integration-test-multi",
        )

        with stub_transformer_source(docs):
            response = execute(request, runtime=runtime)

        assert response.job_id == "integration-test-multi"
        assert response.failures is None
        assert response.successes.count == 3
        assert set(_read_batch_ids(response)) == {"multi001", "multi002", "multi003"}

        for cid in ["multi001", "multi002", "multi003"]:
            assert get_canonical_status(ids_db, cid) == "assigned"

    def test_reuses_existing_ids(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """A work that already has a canonical ID reuses it without claiming from pool."""
        existing_sid: SourceId = ("Work", "sierra-system-number", "b5555")
        seed_identifier(ids_db, existing_sid, "exist001")
        seed_free_ids(ids_db, ["spare001"])  # should remain untouched

        si = make_source_identifier("Work", "sierra-system-number", "b5555")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b5555]"],
            job_id="integration-test-existing",
        )

        with stub_transformer_source([doc]):
            response = execute(request, runtime=runtime)

        assert response.failures is None
        assert response.successes.count == 1
        assert _read_batch_ids(response) == ["exist001"]
        # The spare free ID should still be free
        assert get_canonical_status(ids_db, "spare001") == "free"

    def test_predecessor_inheritance_end_to_end(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """A work with nested sourceIdentifiers gets all IDs minted."""
        seed_free_ids(ids_db, ["nest0001", "nest0002"])

        root_si = make_source_identifier("Work", "sierra-system-number", "b2000")
        item_si = make_source_identifier("Item", "sierra-system-number", "i3000")
        doc = make_work_doc(root_si, items=[{"sourceIdentifier": item_si}])

        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b2000]"],
            job_id="integration-test-nested",
        )

        with stub_transformer_source([doc]):
            response = execute(request, runtime=runtime)

        assert response.failures is None
        assert response.successes.count == 1

        # Verify both IDs were claimed from the pool
        for cid in ["nest0001", "nest0002"]:
            assert get_canonical_status(ids_db, cid) == "assigned"

        # Verify the indexed document has both canonical IDs embedded
        indexed = MockElasticsearchClient.inputs
        assert len(indexed) == 1
        indexed_doc = indexed[0]["_source"]
        assert indexed_doc["state"]["canonicalId"] is not None
        assert indexed_doc["items"][0]["canonicalId"] is not None


# ---------------------------------------------------------------------------
# Tests: handler() with real resolver
# ---------------------------------------------------------------------------


class TestHandlerWithRealResolver:
    """Tests for the handler() entry point with a DB backend."""

    def test_handler_mints_and_returns_response(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """handler() produces a valid response with minted IDs."""
        seed_free_ids(ids_db, ["hand0001"])

        si = make_source_identifier("Work", "sierra-system-number", "b9000")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b9000]"],
            job_id="handler-integration",
        )

        with stub_transformer_source([doc]):
            response = handler(request, runtime=runtime)

        assert isinstance(response, StepManifest)
        assert response.job_id == "handler-integration"
        assert response.successes.count == 1
        assert response.failures is None
        assert get_canonical_status(ids_db, "hand0001") == "assigned"

    def test_handler_reports_failures_on_pool_exhaustion(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """When the free ID pool runs out, affected documents become failures."""
        # No free IDs seeded — pool is empty
        si = make_source_identifier("Work", "sierra-system-number", "b8888")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b8888]"],
            job_id="handler-exhaustion",
        )

        with stub_transformer_source([doc]):
            response = handler(request, runtime=runtime)

        # The transform step catches the RuntimeError from the resolver
        # and records it as a failure
        assert response.job_id == "handler-exhaustion"
        assert response.successes.count == 0
        assert response.failures is not None
        assert response.failures.count == 1
        failure_records = _read_failure_details(response)
        assert "Free ID pool exhausted" in failure_records[0]["detail"]

    def test_handler_empty_request(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """Empty source_identifiers produces an empty response."""
        runtime = _build_runtime(ids_db)
        request = StepFunctionMintingRequest(
            source_identifiers=[],
            job_id="handler-empty",
        )

        with stub_transformer_source([]):
            response = handler(request, runtime=runtime)

        assert response.job_id == "handler-empty"
        assert response.successes.count == 0
        assert response.failures is None


# ---------------------------------------------------------------------------
# Tests: CloudWatch metrics publishing
# ---------------------------------------------------------------------------


class TestMetricsPublishing:
    """Verify that success_count and failure_count metrics are published."""

    def test_publishes_metrics_for_non_dev_pipeline(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """Metrics are published when pipeline_date is not 'dev'."""
        seed_free_ids(ids_db, ["metr0001"])

        si = make_source_identifier("Work", "sierra-system-number", "b6001")
        doc = make_work_doc(si)

        resolver = MintingResolver.from_connection(ids_db)
        config = IdMinterConfig(
            rds_client=RDSClientConfig(password="id_minter"),
            apply_migrations=False,
            pipeline_date="2024-01-01",
        )
        runtime = IdMinterRuntime(
            config=config,
            resolver=resolver,
            source_es_mode="local",
            target_es_mode="local",
        )
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b6001]"],
            job_id="metrics-test-1",
        )

        with stub_transformer_source([doc]):
            handler(request, runtime=runtime)

        metrics = {m["metric_name"]: m for m in MockCloudwatchClient.metrics_reported}
        assert "success_count" in metrics
        assert "failure_count" in metrics
        assert metrics["success_count"]["value"] == 1
        assert metrics["failure_count"]["value"] == 0
        assert metrics["success_count"]["dimensions"] == {"pipeline_date": "2024-01-01"}
        assert metrics["failure_count"]["dimensions"] == {"pipeline_date": "2024-01-01"}

    def test_does_not_publish_metrics_for_dev_pipeline(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """No metrics are emitted when pipeline_date is 'dev'."""
        seed_free_ids(ids_db, ["metr0002"])

        si = make_source_identifier("Work", "sierra-system-number", "b6002")
        doc = make_work_doc(si)

        resolver = MintingResolver.from_connection(ids_db)
        config = IdMinterConfig(
            rds_client=RDSClientConfig(password="id_minter"),
            apply_migrations=False,
            pipeline_date="dev",
        )
        runtime = IdMinterRuntime(
            config=config,
            resolver=resolver,
            source_es_mode="local",
            target_es_mode="local",
        )
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b6002]"],
            job_id="metrics-test-dev",
        )

        with stub_transformer_source([doc]):
            handler(request, runtime=runtime)

        assert MockCloudwatchClient.metrics_reported == []

    def test_publishes_failure_count_when_pool_exhausted(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """failure_count metric reflects actual failures."""
        # No free IDs seeded — pool is empty
        si = make_source_identifier("Work", "sierra-system-number", "b6003")
        doc = make_work_doc(si)

        resolver = MintingResolver.from_connection(ids_db)
        config = IdMinterConfig(
            rds_client=RDSClientConfig(password="id_minter"),
            apply_migrations=False,
            pipeline_date="2024-01-01",
        )
        runtime = IdMinterRuntime(
            config=config,
            resolver=resolver,
            source_es_mode="local",
            target_es_mode="local",
        )
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b6003]"],
            job_id="metrics-test-failure",
        )

        with stub_transformer_source([doc]):
            handler(request, runtime=runtime)

        metrics = {m["metric_name"]: m for m in MockCloudwatchClient.metrics_reported}
        assert metrics["success_count"]["value"] == 0
        assert metrics["failure_count"]["value"] == 1


# ---------------------------------------------------------------------------
# Tests: SNS publishing of successful IDs
# ---------------------------------------------------------------------------


class TestSnsPublishing:
    """Verify that successful canonical IDs are published to SNS."""

    def test_publishes_successful_ids_to_sns(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """Successful canonical IDs are published to the configured SNS topic."""
        seed_free_ids(ids_db, ["sns00001"])

        si = make_source_identifier("Work", "sierra-system-number", "b7001")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db, downstream_sns_topic_arn=TEST_SNS_TOPIC_ARN)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b7001]"],
            job_id="sns-test-1",
        )

        with stub_transformer_source([doc]):
            execute(request, runtime=runtime)

        assert len(MockSNSClient.publish_batch_request_entries) == 1
        call = MockSNSClient.publish_batch_request_entries[0]
        assert call["TopicArn"] == TEST_SNS_TOPIC_ARN

        entries = call["PublishBatchRequestEntries"]
        assert len(entries) == 1
        # The message should be the canonical ID as a plain string
        # (wrapped in {"default": "<id>"} for SNS MessageStructure=json)
        msg = json.loads(entries[0]["Message"])
        assert msg == {"default": "sns00001"}

    def test_publishes_in_batches_of_10(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """IDs are chunked into SNS batches of at most 10."""
        ids = [f"bt{i:06d}" for i in range(25)]
        seed_free_ids(ids_db, ids)

        docs = [
            make_work_doc(
                make_source_identifier("Work", "sierra-system-number", f"b{i}")
            )
            for i in range(25)
        ]

        runtime = _build_runtime(ids_db, downstream_sns_topic_arn=TEST_SNS_TOPIC_ARN)
        request = StepFunctionMintingRequest(
            source_identifiers=[f"Work[sierra-system-number/b{i}]" for i in range(25)],
            job_id="sns-batch-test",
        )

        with stub_transformer_source(docs):
            execute(request, runtime=runtime)

        calls = MockSNSClient.publish_batch_request_entries
        assert len(calls) == 3  # 10 + 10 + 5
        assert len(calls[0]["PublishBatchRequestEntries"]) == 10
        assert len(calls[1]["PublishBatchRequestEntries"]) == 10
        assert len(calls[2]["PublishBatchRequestEntries"]) == 5

        # Collect all published IDs
        published_ids = set()
        for call in calls:
            for entry in call["PublishBatchRequestEntries"]:
                msg = json.loads(entry["Message"])
                published_ids.add(msg["default"])
        assert published_ids == set(ids)

    def test_no_sns_publish_when_topic_arn_is_none(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """No SNS publishing when downstream_sns_topic_arn is not configured."""
        seed_free_ids(ids_db, ["nosns001"])

        si = make_source_identifier("Work", "sierra-system-number", "b8001")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db)  # No SNS ARN
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b8001]"],
            job_id="no-sns-test",
        )

        with stub_transformer_source([doc]):
            response = execute(request, runtime=runtime)

        assert response.successes.count == 1
        assert len(MockSNSClient.publish_batch_request_entries) == 0

    def test_no_sns_publish_when_no_successful_ids(
        self,
        mock_es: None,
        ids_db: pymysql.connections.Connection,
    ) -> None:
        """No SNS publishing when there are no successful IDs (e.g. all failures)."""
        # No free IDs seeded — pool is empty, so minting will fail
        si = make_source_identifier("Work", "sierra-system-number", "b8002")
        doc = make_work_doc(si)

        runtime = _build_runtime(ids_db, downstream_sns_topic_arn=TEST_SNS_TOPIC_ARN)
        request = StepFunctionMintingRequest(
            source_identifiers=["Work[sierra-system-number/b8002]"],
            job_id="no-success-test",
        )

        with stub_transformer_source([doc]):
            execute(request, runtime=runtime)

        assert len(MockSNSClient.publish_batch_request_entries) == 0
