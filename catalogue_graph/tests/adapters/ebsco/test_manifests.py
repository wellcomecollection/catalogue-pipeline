import json

import pytest

import adapters.ebsco.config as adapter_config
from adapters.ebsco.manifests import ManifestWriter
from adapters.ebsco.models.manifests import ErrorLine
from tests.mocks import MockSmartOpen


def _read_ndjson(uri: str) -> list[dict]:
    path = MockSmartOpen.file_lookup[uri]
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def test_manifest_writer_writes_success_only() -> None:
    writer = ManifestWriter(
        job_id="job123",
        changeset_id="chgABC",
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )
    batches_ids = [
        ["Work[ebsco-alt-lookup/id1]", "Work[ebsco-alt-lookup/id2]"],
        ["Work[ebsco-alt-lookup/id3]"],
    ]
    manifest = writer.build_manifest(
        job_id="job123", batches_ids=batches_ids, errors=[], success_count=3
    )

    assert manifest.failures is None
    assert manifest.successes.count == 3
    expected_key = f"{adapter_config.BATCH_S3_PREFIX}/chgABC.job123.ids.ndjson"
    assert manifest.successes.batch_file_location.key == expected_key
    uri = (
        f"s3://{manifest.successes.batch_file_location.bucket}/"  # bucket
        f"{manifest.successes.batch_file_location.key}"
    )
    lines = _read_ndjson(uri)
    assert len(lines) == 2  # two batch lines
    assert lines[0]["jobId"] == "job123"
    assert lines[0]["sourceIdentifiers"] == [
        "Work[ebsco-alt-lookup/id1]",
        "Work[ebsco-alt-lookup/id2]",
    ]
    assert lines[1]["sourceIdentifiers"] == ["Work[ebsco-alt-lookup/id3]"]


def test_manifest_writer_writes_failures() -> None:
    writer = ManifestWriter(
        job_id="job999",
        changeset_id=None,  # triggers 'reindex' naming
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )

    error_lines = [
        ErrorLine(id="e1", message="stage=transform; reason=parse_error"),
        ErrorLine(
            id="e2",
            message="stage=index; status=400; error_type=mapper_parsing_exception",
        ),
    ]

    manifest = writer.build_manifest(
        job_id="job999",
        batches_ids=[["Work[ebsco-alt-lookup/idA]"]],
        errors=error_lines,
        success_count=1,
    )
    assert manifest.failures is not None
    assert manifest.failures.count == 2
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
    assert {line["id"] for line in failure_lines} == {"e1", "e2"}
    messages = {line["id"]: line["message"] for line in failure_lines}
    assert "reason=parse_error" in messages["e1"]
    assert "error_type=mapper_parsing_exception" in messages["e2"]
    # Naming pattern for reindex without changeset
    assert manifest.successes.batch_file_location.key.startswith(
        f"{adapter_config.BATCH_S3_PREFIX}/reindex.job999.ids"
    )


def test_manifest_writer_handles_empty_batches() -> None:
    writer = ManifestWriter(
        job_id="jobEmpty",
        changeset_id="chgEmpty",
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )
    # Even with zero success_count we still write an (empty) success manifest file with 0 count
    manifest = writer.build_manifest(
        job_id="jobEmpty", batches_ids=[], errors=[], success_count=0
    )
    assert manifest.successes.count == 0
    assert manifest.failures is None
    uri = (
        f"s3://{manifest.successes.batch_file_location.bucket}/"
        f"{manifest.successes.batch_file_location.key}"
    )
    lines = _read_ndjson(uri)
    assert lines == []  # no batch lines written


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
        changeset_id=changeset_id,
        bucket=adapter_config.S3_BUCKET,
        prefix=adapter_config.BATCH_S3_PREFIX,
    )
    # include an error to force failure file creation
    manifest = writer.build_manifest(
        job_id=job_id,
        batches_ids=[["Work[ebsco-alt-lookup/only1]"]],
        errors=[ErrorLine(id="x", message="stage=transform; reason=parse_error")],
        success_count=1,
    )
    assert manifest.successes.batch_file_location.key.endswith(expected_success)
    assert manifest.failures is not None
    assert manifest.failures.error_file_location.key.endswith(expected_failure_prefix)
