import json

import pytest

import adapters.ebsco.config as adapter_config
from adapters.transformers.base_transformer import TransformationError
from adapters.transformers.manifests import ManifestWriter
from tests.mocks import MockSmartOpen


def _read_ndjson(uri: str) -> list[dict]:
    path = MockSmartOpen.file_lookup[uri]
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def test_manifest_writer_writes_success_only() -> None:
    writer = ManifestWriter(
        job_id="job123",
        changeset_ids=["chgABC"],
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )
    success_ids = [
        "Work[ebsco-alt-lookup/id1]",
        "Work[ebsco-alt-lookup/id2]",
        "Work[ebsco-alt-lookup/id3]",
    ]
    manifest = writer.build_manifest(successful_ids=success_ids, errors=[])

    assert manifest.failures is None
    assert manifest.successes.count == len(success_ids)
    expected_key = f"{adapter_config.BATCH_S3_PREFIX}/chgABC.job123.ids.ndjson"
    assert manifest.successes.batch_file_location.key == expected_key
    uri = (
        f"s3://{manifest.successes.batch_file_location.bucket}/"  # bucket
        f"{manifest.successes.batch_file_location.key}"
    )
    lines = _read_ndjson(uri)
    assert lines  # at least one batch written
    assert {line["jobId"] for line in lines} == {"job123"}
    written_ids: list[str] = []
    for line in lines:
        written_ids += line["sourceIdentifiers"]
    assert written_ids == success_ids


def test_manifest_writer_writes_failures() -> None:
    writer = ManifestWriter(
        job_id="job999",
        changeset_ids=[],  # triggers 'reindex' naming
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )

    errors = [
        TransformationError(
            work_id="Work[ebsco-alt-lookup/e1]",
            stage="transform",
            detail="reason=parse_error",
        ),
        TransformationError(
            work_id="Work[ebsco-alt-lookup/e2]",
            stage="index",
            detail="status=400; error_type=mapper_parsing_exception",
        ),
    ]

    manifest = writer.build_manifest(
        successful_ids=["Work[ebsco-alt-lookup/idA]"],
        errors=errors,
    )
    assert manifest.failures is not None
    assert manifest.failures.count == len(errors)
    # Check success file still written
    success_uri = (
        f"s3://{manifest.successes.batch_file_location.bucket}/"
        f"{manifest.successes.batch_file_location.key}"
    )
    success_lines = _read_ndjson(success_uri)
    assert success_lines[0]["sourceIdentifiers"] == ["Work[ebsco-alt-lookup/idA]"]

    # Check failure file contents
    failure_uri = (
        f"s3://{manifest.failures.error_file_location.bucket}/"
        f"{manifest.failures.error_file_location.key}"
    )
    failure_lines = _read_ndjson(failure_uri)
    assert {line["work_id"] for line in failure_lines} == {
        "Work[ebsco-alt-lookup/e1]",
        "Work[ebsco-alt-lookup/e2]",
    }
    details = {line["work_id"]: line["detail"] for line in failure_lines}
    assert "reason=parse_error" in details["Work[ebsco-alt-lookup/e1]"]
    assert "error_type=mapper_parsing_exception" in details["Work[ebsco-alt-lookup/e2]"]
    # Naming pattern for reindex without changeset
    assert manifest.successes.batch_file_location.key.startswith(
        f"{adapter_config.BATCH_S3_PREFIX}/reindex.job999.ids"
    )


def test_manifest_writer_handles_empty_batches() -> None:
    writer = ManifestWriter(
        job_id="jobEmpty",
        changeset_ids=["chgEmpty"],
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )
    manifest = writer.build_manifest(successful_ids=[], errors=[])
    assert manifest.successes.count == 0
    assert manifest.failures is None
    uri = (
        f"s3://{manifest.successes.batch_file_location.bucket}/"
        f"{manifest.successes.batch_file_location.key}"
    )
    lines = _read_ndjson(uri)
    assert lines == []  # no batch lines written for empty set


@pytest.mark.parametrize(
    "changeset_id, job_id, expected_success, expected_failure_prefix",
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
def test_manifest_file_naming_patterns(
    changeset_id: str | None,
    job_id: str,
    expected_success: str,
    expected_failure_prefix: str,
) -> None:
    """Ensure naming patterns match spec for both changeset & reindex cases."""
    writer = ManifestWriter(
        job_id=job_id,
        changeset_ids=[changeset_id] if changeset_id else [],
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )
    manifest = writer.build_manifest(
        successful_ids=["Work[ebsco-alt-lookup/only1]"],
        errors=[
            TransformationError(
                work_id="Work[ebsco-alt-lookup/only1]",
                stage="transform",
                detail="reason=parse_error",
            )
        ],
    )
    assert manifest.successes.batch_file_location.key.endswith(expected_success)
    assert manifest.failures is not None
    assert manifest.failures.error_file_location.key.endswith(expected_failure_prefix)
