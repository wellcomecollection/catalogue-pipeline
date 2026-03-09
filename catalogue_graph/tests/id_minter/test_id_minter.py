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
from tests.mocks import MockElasticsearchClient, MockSmartOpen
from utils.models.manifests import StepManifest

pytestmark = pytest.mark.database


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _build_runtime(
    conn: pymysql.connections.Connection,
) -> IdMinterRuntime:
    """Build a runtime with a MintingResolver backed by the DB connection."""
    resolver = MintingResolver.from_connection(conn)
    config = IdMinterConfig(
        rds_client=RDSClientConfig(password="id_minter"),
        apply_migrations=False,
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
