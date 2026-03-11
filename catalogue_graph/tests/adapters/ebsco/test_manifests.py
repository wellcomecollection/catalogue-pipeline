"""Tests for TransformerManifestWriter-specific behaviour.

Base ManifestWriter mechanics (file writing, batching, failures) are tested
in tests/utils/test_manifests.py.  These tests cover:
- changeset-based label naming (changeset vs reindex)
- sourceIdentifiers field in batch lines
- TransformerManifest return type with changeset_ids
"""

import json
from pathlib import PurePosixPath

import pytest

import adapters.ebsco.config as adapter_config
from adapters.transformers.manifests import (
    TransformerManifest,
    TransformerManifestWriter,
)
from core.transformer import TransformationError
from tests.mocks import MockSmartOpen


def _read_ndjson(uri: str) -> list[dict]:
    path = MockSmartOpen.file_lookup[uri]
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def test_batch_lines_use_source_identifiers_field() -> None:
    writer = TransformerManifestWriter(
        job_id="job123",
        changeset_ids=["chgABC"],
        bucket=adapter_config.S3_BUCKET,
        prefix=str(PurePosixPath(adapter_config.S3_PREFIX, "transformer/batches")),
    )
    success_ids = [
        "Work[ebsco-alt-lookup/id1]",
        "Work[ebsco-alt-lookup/id2]",
    ]
    manifest = writer.build_manifest(successful_ids=success_ids, errors=[])

    loc = manifest.successes.batch_file_location
    uri = f"s3://{loc.bucket}/{loc.key}"
    lines = _read_ndjson(uri)
    written_ids: list[str] = []
    for line in lines:
        written_ids += line["sourceIdentifiers"]
    assert written_ids == success_ids


def test_returns_transformer_manifest_with_changeset_ids() -> None:
    writer = TransformerManifestWriter(
        job_id="job1",
        changeset_ids=["chg1", "chg2"],
        bucket=adapter_config.S3_BUCKET,
        prefix=str(PurePosixPath(adapter_config.S3_PREFIX, "transformer/batches")),
    )
    manifest = writer.build_manifest(successful_ids=["id1"], errors=[])

    assert isinstance(manifest, TransformerManifest)
    assert manifest.changeset_ids == ["chg1", "chg2"]


@pytest.mark.parametrize(
    "changeset_id, job_id, expected_success, expected_failure",
    [
        (
            "chgXYZ",
            "job777",
            "chgXYZ.job777.ids.ndjson",
            "chgXYZ.job777.ids.failures.ndjson",
        ),
        (
            None,
            "job555",
            "reindex.job555.ids.ndjson",
            "reindex.job555.ids.failures.ndjson",
        ),
    ],
)
def test_changeset_naming_patterns(
    changeset_id: str | None,
    job_id: str,
    expected_success: str,
    expected_failure: str,
) -> None:
    """Label is derived from changeset_ids; empty list falls back to 'reindex'."""
    writer = TransformerManifestWriter(
        job_id=job_id,
        changeset_ids=[changeset_id] if changeset_id else [],
        bucket=adapter_config.S3_BUCKET,
        prefix=str(PurePosixPath(adapter_config.S3_PREFIX, "transformer/batches")),
    )
    manifest = writer.build_manifest(
        successful_ids=["Work[ebsco-alt-lookup/only1]"],
        errors=[
            TransformationError(
                row_id="only1",
                stage="transform",
                detail="reason=parse_error",
            )
        ],
    )
    assert manifest.successes.batch_file_location.key.endswith(expected_success)
    assert manifest.failures is not None
    assert manifest.failures.error_file_location.key.endswith(expected_failure)
