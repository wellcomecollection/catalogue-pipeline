import os
from pyiceberg.catalog import load_catalog
from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, IntegerType, StringType
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

import pyarrow as pa
import uuid

from pyiceberg.expressions import Not, IsNull
import xml.etree.ElementTree as ET
import sys
from datetime import datetime, timezone

from iceberg_updates import get_table, update_table
from schemata import ARROW_SCHEMA

EBSCO_NAMESPACE = "ebsco"


def update_from_xml_file(table: IcebergTable, xmlfile):
    return update_from_xml(table, ET.parse(xmlfile).getroot())


def update_from_xml(table: IcebergTable, collection: ET.ElementTree):
    return update_table(
        table,
        data_to_pa_table([node_to_record(record_node) for record_node in collection]),
    )


def node_to_record(node: ET.ElementTree):
    ebsco_id = node.find(
        "{http://www.loc.gov/MARC21/slim}controlfield[@tag='001']"
    ).text
    return {"namespace": EBSCO_NAMESPACE, "id": ebsco_id, "content": ET.tostring(node)}


def data_to_pa_table(data):
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def main(xmlfile):
    """Do Something"""
    table = get_table(
        catalogue_name="local",
        catalogue_uri="sqlite:////tmp/warehouse/catalog.db",
        catalogue_warehouse="file:///tmp/warehouse/",
        catalogue_namespace="default",
        table_name="mytable"
    )
    return update_from_xml_file(table, xmlfile)


if __name__ == "__main__":
    print(main(sys.stdin))
