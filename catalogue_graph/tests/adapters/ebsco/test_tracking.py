import json

from adapters.ebsco.utils.tracking import (
    ProcessedFileRecord,
    record_processed_file,
)
from adapters.transformers.transformer import TransformerEvent
from utils.aws import pydantic_from_s3_json

from tests.mocks import MockSmartOpen


class TestRecordProcessedFile:
    def test_record_processed_file(self) -> None:
        job_id = "test-job-id"
        file_location = "s3://s3-bucket/is-a/file.xml"
        event = TransformerEvent(
            transformer_type="ebsco",
            job_id=job_id,
            changeset_ids=["I have changed"],
        )
        record = record_processed_file(
            job_id=job_id, file_location=file_location, step="loaded", payload_obj=event
        )

        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == job_id
        assert record.payload["changeset_ids"] == ["I have changed"]
        assert record.step == "loaded"

        # Verify smart_open was called with the correct S3 URI
        expected_uri = "s3://s3-bucket/is-a/file.xml.loaded.json"
        with open(MockSmartOpen.file_lookup[expected_uri]) as f:
            written = json.loads(f.read())

        # Verify the JSON was written correctly
        assert written["job_id"] == job_id
        assert written["step"] == "loaded"
        assert written["payload"]["changeset_ids"] == ["I have changed"]


class TestIsFileAlreadyProcessed:
    def test_file_already_processed(self) -> None:
        file_location = "s3://test-bucket/dev/ftp_v2/existing-file.xml"

        prior_event = TransformerEvent(
            transformer_type="ebsco",
            job_id="jid",
            changeset_ids=["cid"],
        )
        stored = ProcessedFileRecord(
            job_id="jid", step="loaded", payload=prior_event.model_dump()
        )
        MockSmartOpen.mock_s3_file(f"{file_location}.loaded.json", stored.model_dump_json())

        record = pydantic_from_s3_json(
            ProcessedFileRecord, f"{file_location}.loaded.json"
        )
        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == "jid"
        assert record.get("changeset_ids") == ["cid"]

    def test_file_not_yet_processed(self) -> None:
        file_location = "s3://test-bucket/dev/ftp_v2/non-existent-file.xml"
        record = pydantic_from_s3_json(
            ProcessedFileRecord, f"{file_location}.loaded.json", ignore_missing=True
        )
        assert record is None
