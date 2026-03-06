"""Tests for the shared ManifestWriter base class."""

import json

import pytest
from pydantic import BaseModel

from tests.mocks import MockSmartOpen
from utils.manifests import BatchLine, ManifestWriter
from utils.models.manifests import StepManifest

# -- Concrete subclass for testing the base mechanics -------------------------


class _TestBatchLine(BatchLine):
    ids: list[str]


class _TestManifestWriter(ManifestWriter):
    def _make_batch_line(self, ids: list[str]) -> _TestBatchLine:
        return _TestBatchLine(ids=ids, jobId=self.job_id)


class _ErrorModel(BaseModel):
    row_id: str
    detail: str


# -- Helpers ------------------------------------------------------------------


def _read_ndjson(uri: str) -> list[dict]:
    path = MockSmartOpen.file_lookup[uri]
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def _success_uri(manifest: StepManifest) -> str:
    loc = manifest.successes.batch_file_location
    return f"s3://{loc.bucket}/{loc.key}"


def _failure_uri(manifest: StepManifest) -> str:
    assert manifest.failures is not None
    loc = manifest.failures.error_file_location
    return f"s3://{loc.bucket}/{loc.key}"


def _make_writer(label: str = "test", job_id: str = "job1") -> _TestManifestWriter:
    return _TestManifestWriter(
        job_id=job_id,
        label=label,
        bucket="test-bucket",
        prefix="manifests",
    )


# -- Tests --------------------------------------------------------------------


class TestSuccessManifest:
    def test_writes_ids_to_ndjson(self) -> None:
        writer = _make_writer()
        manifest = writer.build_manifest(
            successful_ids=["id1", "id2", "id3"], errors=[]
        )

        assert manifest.successes.count == 3
        lines = _read_ndjson(_success_uri(manifest))
        all_ids = [id for line in lines for id in line["ids"]]
        assert all_ids == ["id1", "id2", "id3"]

    def test_includes_job_id_in_batch_lines(self) -> None:
        writer = _make_writer(job_id="my-job")
        manifest = writer.build_manifest(successful_ids=["x"], errors=[])

        lines = _read_ndjson(_success_uri(manifest))
        assert all(line["jobId"] == "my-job" for line in lines)

    def test_empty_ids_produces_empty_file(self) -> None:
        writer = _make_writer()
        manifest = writer.build_manifest(successful_ids=[], errors=[])

        assert manifest.successes.count == 0
        lines = _read_ndjson(_success_uri(manifest))
        assert lines == []

    def test_no_failures_when_errors_empty(self) -> None:
        writer = _make_writer()
        manifest = writer.build_manifest(successful_ids=["a"], errors=[])
        assert manifest.failures is None


class TestFailureManifest:
    def test_writes_errors_to_ndjson(self) -> None:
        errors = [
            _ErrorModel(row_id="e1", detail="boom"),
            _ErrorModel(row_id="e2", detail="crash"),
        ]
        writer = _make_writer()
        manifest = writer.build_manifest(successful_ids=[], errors=errors)

        assert manifest.failures is not None
        assert manifest.failures.count == 2
        lines = _read_ndjson(_failure_uri(manifest))
        assert {line["row_id"] for line in lines} == {"e1", "e2"}

    def test_success_file_still_written_alongside_failures(self) -> None:
        errors = [_ErrorModel(row_id="e1", detail="oops")]
        writer = _make_writer()
        manifest = writer.build_manifest(successful_ids=["ok1"], errors=errors)

        assert manifest.successes.count == 1
        assert manifest.failures is not None
        assert manifest.failures.count == 1
        # Both files should be readable
        assert _read_ndjson(_success_uri(manifest))[0]["ids"] == ["ok1"]
        assert _read_ndjson(_failure_uri(manifest))[0]["row_id"] == "e1"


class TestFileNaming:
    def test_success_filename(self) -> None:
        writer = _make_writer(label="myLabel", job_id="j42")
        manifest = writer.build_manifest(successful_ids=["x"], errors=[])
        assert manifest.successes.batch_file_location.key == (
            "manifests/myLabel.j42.ids.ndjson"
        )

    def test_failure_filename(self) -> None:
        errors = [_ErrorModel(row_id="e1", detail="fail")]
        writer = _make_writer(label="myLabel", job_id="j42")
        manifest = writer.build_manifest(successful_ids=[], errors=errors)
        assert manifest.failures is not None
        assert manifest.failures.error_file_location.key == (
            "manifests/myLabel.j42.ids.failures.ndjson"
        )

    def test_bucket_is_set(self) -> None:
        writer = _make_writer()
        manifest = writer.build_manifest(successful_ids=["x"], errors=[])
        assert manifest.successes.batch_file_location.bucket == "test-bucket"


class TestBatching:
    def test_large_id_list_is_batched(self) -> None:
        from utils.manifests import BATCH_SIZE

        ids = [f"id{i}" for i in range(BATCH_SIZE + 100)]
        writer = _make_writer()
        manifest = writer.build_manifest(successful_ids=ids, errors=[])

        lines = _read_ndjson(_success_uri(manifest))
        assert len(lines) == 2  # one full batch + one partial
        assert len(lines[0]["ids"]) == BATCH_SIZE
        assert len(lines[1]["ids"]) == 100
        assert manifest.successes.count == BATCH_SIZE + 100


class TestAbstractContract:
    def test_base_class_raises_not_implemented(self) -> None:
        writer = ManifestWriter(job_id="j", label="l", bucket="b", prefix="p")
        with pytest.raises(NotImplementedError):
            writer.build_manifest(successful_ids=["x"], errors=[])
