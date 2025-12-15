from collections.abc import Callable
from typing import TextIO

import pytest
from lxml import etree

from adapters.ebsco.marcxml_loader import (
    MarcXmlFileLoader,
    MissingRecordIdentifierError,
)
from adapters.ebsco.steps.loader import EBSCO_NAMESPACE
from adapters.utils.schemata import ARROW_SCHEMA
from tests.mocks import MockSmartOpen

# -- Tests for MarcXmlFileLoader.extract_record_id ---
file_loader = MarcXmlFileLoader(schema=ARROW_SCHEMA, namespace=EBSCO_NAMESPACE)


def test_uses_controlfield_001_when_available() -> None:
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

    assert MarcXmlFileLoader.extract_record_id(node) == "r-control"


def test_falls_back_to_datafield_035_removing_prefix() -> None:
    node = etree.fromstring(
        """
        <record>
            <datafield tag="035">
                <subfield code="a">(OCoLC)814782</subfield>
            </datafield>
        </record>
        """
    )

    assert MarcXmlFileLoader.extract_record_id(node) == "814782"


def test_ignores_controlfield_001_when_empty() -> None:
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
    assert MarcXmlFileLoader.extract_record_id(node) == "9999999"


def test_raises_when_no_identifier_present() -> None:
    with pytest.raises(
        MissingRecordIdentifierError,
        match="Could not find controlfield 001 or usable datafield 035",
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
        MarcXmlFileLoader.extract_record_id(node)


def test_raises_when_no_identifier_fields_present() -> None:
    node = etree.fromstring("<record></record>")

    with pytest.raises(
        MissingRecordIdentifierError,
        match="Could not find controlfield 001 or usable datafield 035",
    ):
        MarcXmlFileLoader.extract_record_id(node)


def _canonicalize(xml: str) -> str:
    return etree.tostring(
        etree.fromstring(xml, parser=etree.XMLParser(remove_blank_text=True)),
        encoding="unicode",
        pretty_print=False,
    )


@pytest.fixture
def register_mock_open() -> Callable[[str], None]:
    def _register(path: str) -> None:
        with open(path, "rb") as fh:
            MockSmartOpen.mock_s3_file(path, fh.read())

    return _register


# -- Tests for MarcXmlFileLoader.load_file ---


def test_loads_one_record_into_pa_table(
    xml_with_one_record: TextIO, register_mock_open: Callable[[str], None]
) -> None:
    register_mock_open(xml_with_one_record.name)

    table = file_loader.load_file(xml_with_one_record.name)

    assert table.schema == ARROW_SCHEMA
    rows = [
        {**row, "content": _canonicalize(row["content"])} for row in table.to_pylist()
    ]
    assert rows == [
        {
            "namespace": EBSCO_NAMESPACE,
            "id": "ebs00001",
            "content": _canonicalize(
                """
                <record xmlns="http://www.loc.gov/MARC21/slim">
                    <leader>00000cas a22000003 4500</leader>
                    <controlfield tag="001">ebs00001</controlfield>
                    <datafield tag="210" ind1=" " ind2=" ">
                        <subfield code="a">How to Avoid Huge Ships</subfield>
                    </datafield>
                </record>
                """
            ),
        }
    ]


def test_loads_multiple_records_preserving_content_and_order(
    xml_with_three_records: TextIO,
    register_mock_open: Callable[[str], None],
) -> None:
    register_mock_open(xml_with_three_records.name)
    table_three = file_loader.load_file(xml_with_three_records.name)

    assert table_three.schema == ARROW_SCHEMA

    rows_three = [
        {**row, "content": _canonicalize(row["content"])}
        for row in table_three.to_pylist()
    ]

    assert rows_three == [
        {
            "namespace": EBSCO_NAMESPACE,
            "id": "ebs00001",
            "content": _canonicalize(
                """
                <record xmlns="http://www.loc.gov/MARC21/slim">
                    <leader>00000cas a22000003 4500</leader>
                    <controlfield tag="001">ebs00001</controlfield>
                    <datafield tag="210" ind1=" " ind2=" ">
                        <subfield code="a">How to Avoid Huge Ships</subfield>
                    </datafield>
                    <datafield tag="700" ind1=" " ind2=" ">
                        <subfield code="a">John W. Trimmer</subfield>
                    </datafield>
                </record>
                """
            ),
        },
        {
            "namespace": EBSCO_NAMESPACE,
            "id": "ebs00003",
            "content": _canonicalize(
                """
                <record xmlns="http://www.loc.gov/MARC21/slim">
                    <leader>00000cas a22000003 4500</leader>
                    <controlfield tag="001">ebs00003</controlfield>
                    <datafield tag="210" ind1=" " ind2=" ">
                        <subfield code="a">European Archives of Psychiatry and Clinical Neuroscience</subfield>
                    </datafield>
                </record>
                """
            ),
        },
        {
            "namespace": EBSCO_NAMESPACE,
            "id": "ebs00004",
            "content": _canonicalize(
                """
                <record xmlns="http://www.loc.gov/MARC21/slim">
                    <leader>00000cas a22000003 4500</leader>
                    <controlfield tag="001">ebs00004</controlfield>
                    <datafield tag="210" ind1=" " ind2=" ">
                        <subfield code="a">Brain: a journal of neurology</subfield>
                    </datafield>
                </record>
                """
            ),
        },
    ]


def test_raises_on_invalid_xml_file(
    not_xml: TextIO, register_mock_open: Callable[[str], None]
) -> None:
    register_mock_open(not_xml.name)
    with pytest.raises(etree.XMLSyntaxError):
        file_loader.load_file(not_xml.name)
