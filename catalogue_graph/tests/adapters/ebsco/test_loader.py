import json

import pytest
from lxml import etree
from pyiceberg.table import Table as IcebergTable

from adapters.ebsco.models.step_events import (
    EbscoAdapterLoaderEvent,
    EbscoAdapterTransformerEvent,
)
from adapters.ebsco.steps.loader import (
    EbscoAdapterLoaderConfig,
    extract_record_id,
    handler,
)
from adapters.ebsco.utils.tracking import ProcessedFileRecord
from tests.mocks import MockSmartOpen


class TestRecordIdExtraction:
    def test_uses_controlfield_001_when_available(self) -> None:
        node = etree.fromstring(
            """
            <record>
                <controlfield tag="001">r-control</controlfield>
                <datafield tag="035">
                    <subfield code="a">(OCoLC)9999999</subfield>
                </datafield>
            </record>
            """
        )

        assert extract_record_id(node) == "r-control"

    def test_falls_back_to_datafield_035_removing_prefix(self) -> None:
        node = etree.fromstring(
            """
            <record>
                <datafield tag="035">
                    <subfield code="a">(OCoLC)814782</subfield>
                </datafield>
            </record>
            """
        )

        assert extract_record_id(node) == "814782"

    def test_ignores_controlfield_001_when_empty(self) -> None:
        node = etree.fromstring(
            """
            <record>
                <controlfield tag="001"></controlfield>
                <datafield tag="035">
                    <subfield code="a">(OCoLC)9999999</subfield>
                </datafield>
            </record>
            """
        )
        assert extract_record_id(node) == "9999999"

    def test_raises_when_no_identifier_present(self) -> None:
        node = etree.fromstring("<record></record>")

        with pytest.raises(
                Exception, match="Could not find controlfield 001 or usable datafield 035"
        ):
            node = etree.fromstring(
                """
                <record>
                    <controlfield tag="001">   </controlfield>
                    <datafield tag="035">
                        <subfield code="a"> (OCoLC) </subfield>
                    </datafield>
                </record>
                """
            )
        assert extract_record_id(node)

    def test_raises_when_no_identifier_fields_present(self) -> None:
        node = etree.fromstring("<record></record>")

        with pytest.raises(
            Exception, match="Could not find controlfield 001 or usable datafield 035"
        ):
            extract_record_id(node)


class TestLoaderHandler:
    def test_short_circuit_when_prior_processed_detected(self) -> None:
        """Loader should short-circuit using existing tracking file."""
        file_uri = "s3://bucket/path/file.xml"
        tracking_uri = f"{file_uri}.loaded.json"
        prior_event = EbscoAdapterTransformerEvent(
            job_id="20250101T1200",
            changeset_id="prev-change-123",
        )
        tracking_record = ProcessedFileRecord(
            job_id="20250101T1200", step="loaded", payload=prior_event.model_dump()
        )
        MockSmartOpen.mock_s3_file(
            tracking_uri, json.dumps(tracking_record.model_dump())
        )

        event = EbscoAdapterLoaderEvent(job_id="20250101T1200", file_location=file_uri)
        config = EbscoAdapterLoaderConfig(use_rest_api_table=False)
        result = handler(event=event, config_obj=config)

        assert isinstance(result, EbscoAdapterTransformerEvent)
        assert result.changeset_id == "prev-change-123"

    def test_normal_processing_path_records_file(
        self, monkeypatch: pytest.MonkeyPatch, temporary_table: IcebergTable
    ) -> None:
        sample_xml = (
            "<collection>"
            "<record><controlfield tag='001'>r1</controlfield></record>"
            "<record><controlfield tag='001'>r2</controlfield></record>"
            "</collection>"
        )
        file_uri = "s3://bucket/path/file.xml"
        MockSmartOpen.mock_s3_file(file_uri, sample_xml.encode("utf-8"))

        monkeypatch.setattr(
            "adapters.ebsco.utils.iceberg.get_local_table",
            lambda **kwargs: temporary_table,
        )

        event = EbscoAdapterLoaderEvent(job_id="20250101T1200", file_location=file_uri)
        config = EbscoAdapterLoaderConfig(use_rest_api_table=False)
        result = handler(event=event, config_obj=config)

        assert isinstance(result, EbscoAdapterTransformerEvent)
        assert result.changeset_id is not None

        tracking_uri = f"{file_uri}.loaded.json"
        assert tracking_uri in MockSmartOpen.file_lookup
        tracking_path = MockSmartOpen.file_lookup[tracking_uri]
        with open(tracking_path, encoding="utf-8") as f:
            tracking_json = json.load(f)
        assert tracking_json["job_id"] == "20250101T1200"
        assert tracking_json["step"] == "loaded"
        assert tracking_json["payload"]["changeset_id"] == result.changeset_id
