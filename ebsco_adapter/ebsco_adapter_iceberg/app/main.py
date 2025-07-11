from pyiceberg.table import Table as IcebergTable

import pyarrow as pa

import sys

from iceberg_updates import update_table
from schemata import ARROW_SCHEMA
from table_config import get_local_table

from lxml import etree

XMLPARSER = etree.XMLParser(remove_blank_text=True)
EBSCO_NAMESPACE = "ebsco"


def canonicalize(xml_string):
    return xml_string


def update_from_xml_file(table: IcebergTable, xmlfile):
    return update_from_xml(table, load_xml(xmlfile))


def load_xml(xmlfile) -> etree._Element:
    return etree.parse(xmlfile, parser=XMLPARSER).getroot()


def update_from_xml(table: IcebergTable, collection: etree._Element):
    records = nodes_to_records(collection)
    return update_table(
        table,
        data_to_pa_table(
            records
        ),  # [node_to_record(record_node) for record_node in collection]),
        EBSCO_NAMESPACE,
    )


def nodes_to_records(collection: etree._Element):
    return [node_to_record(record_node) for record_node in collection]


def node_to_record(node: etree._Element):
    ebsco_id = extract_id(node)
    return {
        "namespace": EBSCO_NAMESPACE,
        "id": ebsco_id,
        "content": canonicalize(etree.tostring(node, encoding='unicode')),
    }


def extract_id(node: etree._Element) -> str:
    id_field = node.find("./{http://www.loc.gov/MARC21/slim}controlfield[@tag='001']")
    if id_field is None:
        raise ValueError(f"id controlfield could not be found in {node}")
    return id_field.text or ""


def data_to_pa_table(data):
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def main(xmlfile):
    table = get_local_table()
    return update_from_xml_file(table, xmlfile)


if __name__ == "__main__":
    print(main(sys.stdin))
