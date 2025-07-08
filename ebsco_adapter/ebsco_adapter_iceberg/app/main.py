from pyiceberg.table import Table as IcebergTable

import pyarrow as pa

import xml.etree.ElementTree as ET
import sys

from iceberg_updates import get_table, update_table
from schemata import ARROW_SCHEMA

EBSCO_NAMESPACE = "ebsco"


def canonicalize(xml_string):
    return ET.canonicalize(xml_string, strip_text=True)


def update_from_xml_file(table: IcebergTable, xmlfile):
    ET.register_namespace("", "http://www.loc.gov/MARC21/slim")
    return update_from_xml(table, ET.parse(xmlfile).getroot())


def update_from_xml(table: IcebergTable, collection: ET.ElementTree):
    return update_table(
        table,
        data_to_pa_table([node_to_record(record_node) for record_node in collection]),
        EBSCO_NAMESPACE,
    )


def node_to_record(node: ET.ElementTree):
    ebsco_id = node.find(
        "marc:controlfield[@tag='001']",
        namespaces={"marc": "http://www.loc.gov/MARC21/slim"},
    ).text
    return {"namespace": EBSCO_NAMESPACE, "id": ebsco_id, "content": canonicalize(ET.tostring(node))}


def data_to_pa_table(data):
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def main(xmlfile):
    table = get_table(
        catalogue_name="local",
        catalogue_uri="sqlite:////tmp/warehouse/catalog.db",
        catalogue_warehouse="file:///tmp/warehouse/",
        catalogue_namespace="default",
        table_name="mytable",
    )
    return update_from_xml_file(table, xmlfile)


if __name__ == "__main__":
    print(main(sys.stdin))
