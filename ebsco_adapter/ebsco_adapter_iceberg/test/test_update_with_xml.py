import pytest
from pyiceberg.expressions import EqualTo
from pyiceberg.table import Table as IcebergTable
from typing import Any

from main import update_from_xml_file


def test_store_record(temporary_table: IcebergTable, xml_with_one_record: Any) -> None:
    """The XML from each record is serialised and stored in the content field."""
    update_from_xml_file(temporary_table, xml_with_one_record)
    expected_content = """
    <record xmlns="http://www.loc.gov/MARC21/slim"><leader>00000cas a22000003 4500</leader><controlfield tag="001">ebs00001</controlfield><datafield tag="210" ind1=" " ind2=" "><subfield code="a">How to Avoid Huge Ships</subfield></datafield></record>
    """.strip()
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00001")
    ).to_arrow()
    assert pa_table.column("content")[0].as_py() == expected_content


def test_delete_record(
    temporary_table: IcebergTable, xml_with_one_record: Any, xml_with_two_records: Any
) -> None:
    """
    Given a table containing a record ebs00002,
    When a new XML file with no record ebs00002 is imported
    Then the ebs00002 row will be blanked
    """
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


def test_change_record(
    temporary_table: IcebergTable, xml_with_one_record: Any, xml_with_three_records: Any
) -> None:
    """
    Given a table containing a record ebs00001,
    When a new XML file with a different ebs00001 record is imported
    Then the ebs00001 row will contain the new data
    """
    update_from_xml_file(temporary_table, xml_with_one_record)
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00001")
    ).to_arrow()
    assert "John W. Trimmer" not in pa_table.column("content")[0].as_py()
    update_from_xml_file(temporary_table, xml_with_three_records)
    pa_table = temporary_table.scan(
        selected_fields=["content"], row_filter=EqualTo("id", "ebs00001")
    ).to_arrow()
    assert "John W. Trimmer" in pa_table.column("content")[0].as_py()


def test_corrupt_input(temporary_table: IcebergTable, not_xml: Any) -> None:
    """
    Given an update file that cannot be understood
    Then an Exception is raised
    """
    with pytest.raises(Exception):
        update_from_xml_file(temporary_table, not_xml)
