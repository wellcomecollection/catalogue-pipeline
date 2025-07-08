from main import update_from_xml_file, data_to_pa_table, EBSCO_NAMESPACE
from fixtures import (
    temporary_table,
    xml_with_one_record,
    xml_with_two_records,
    xml_with_three_records,
)
from schemata import ARROW_SCHEMA
from pyiceberg.expressions import EqualTo
import xml.etree.ElementTree as ET


def data_to_namespaced_table(unqualified_data):
    return data_to_pa_table([add_namespace(entry) for entry in unqualified_data])


def add_namespace(d):
    d["namespace"] = EBSCO_NAMESPACE
    return d


def canonicalize(xml_string):
    return ET.canonicalize(xml_string, strip_text=True)


def test_store_record(temporary_table, xml_with_one_record):
    update_from_xml_file(temporary_table, xml_with_one_record)
    expected_content = ET.canonicalize(
        """<record xmlns="http://www.loc.gov/MARC21/slim"><leader>00000cas a22000003 4500</leader><controlfield tag="001">ebs00001</controlfield><datafield tag="210" ind1=" " ind2=" "><subfield code="a">How to Avoid Huge Ships</subfield></datafield></record>"""
    )
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00001")
    ).to_arrow()
    assert canonicalize(pa_table.column("content")[0].as_py()) == expected_content


def test_delete_record(temporary_table, xml_with_one_record, xml_with_two_records):
    update_from_xml_file(temporary_table, xml_with_two_records)
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00002")
    ).to_arrow()
    assert pa_table.column("content")[0].as_py() is not None
    update_from_xml_file(temporary_table, xml_with_one_record)
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00002")
    ).to_arrow()
    assert pa_table.column("content")[0].as_py() is None


def test_change_record(temporary_table, xml_with_one_record, xml_with_three_records):
    update_from_xml_file(temporary_table, xml_with_one_record)
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00001")
    ).to_arrow()
    assert not "John W. Trimmer" in pa_table.column("content")[0].as_py()
    update_from_xml_file(temporary_table, xml_with_three_records)
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00001")
    ).to_arrow()
    assert "John W. Trimmer" in pa_table.column("content")[0].as_py()
