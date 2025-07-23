import os
from pyiceberg.table import Table as IcebergTable
from typing import Any, Dict, List, IO, Optional

import pyarrow as pa
import sys
import argparse

from iceberg_updates import update_table
from schemata import ARROW_SCHEMA
from table_config import get_local_table, get_glue_table
import smart_open
import config

from lxml import etree

XMLPARSER = etree.XMLParser(remove_blank_text=True)
EBSCO_NAMESPACE = "ebsco"


def update_from_xml_file(table: IcebergTable, xmlfile: IO[bytes]) -> Optional[str]:
    return update_from_xml(table, load_xml(xmlfile))


def load_xml(xmlfile: IO[bytes]) -> etree._Element:
    return etree.parse(xmlfile, parser=XMLPARSER).getroot()


def update_from_xml(table: IcebergTable, collection: etree._Element) -> Optional[str]:
    records = nodes_to_records(collection)
    return update_table(
        table,
        data_to_pa_table(
            records
        ),  # [node_to_record(record_node) for record_node in collection]),
        EBSCO_NAMESPACE,
    )


def nodes_to_records(collection: etree._Element) -> List[Dict[str, str]]:
    return [node_to_record(record_node) for record_node in collection]


def node_to_record(node: etree._Element) -> Dict[str, str]:
    ebsco_id = extract_id(node)
    return {
        "namespace": EBSCO_NAMESPACE,
        "id": ebsco_id,
        "content": etree.tostring(node, encoding="unicode"),
    }


def extract_id(node: etree._Element) -> str:
    id_field = node.find("./{http://www.loc.gov/MARC21/slim}controlfield[@tag='001']")
    if id_field is None:
        raise ValueError(f"id controlfield could not be found in {node}")
    return id_field.text or ""


def data_to_pa_table(data: List[Dict[str, str]]) -> pa.Table:
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def handler(table: IcebergTable, xml_file_location: str) -> Optional[str]:
    with smart_open.open(xml_file_location, "rb") as f:
        return update_from_xml_file(table, f)


def local_handler() -> Optional[str]:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument("xmlfile", help="Path to the XML file to process")
    parser.add_argument(
        "--use-glue-table",
        action="store_true",
        help="Use AWS Glue table instead of local table",
    )

    args = parser.parse_args()

    if not os.path.isfile(args.xmlfile):
        print(f"Error: {args.xmlfile} does not exist or is not a file.")
        sys.exit(1)

    if args.use_glue_table:
        print("Using AWS Glue table...")
        table = get_glue_table(
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            table_name=config.GLUE_TABLE_NAME,
            namespace=config.GLUE_NAMESPACE,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
        )
    else:
        print("Using local table...")
        table = get_local_table(
            table_name=config.LOCAL_TABLE_NAME,
            namespace=config.LOCAL_NAMESPACE,
            db_name=config.LOCAL_DB_NAME,
        )

    return handler(table, args.xmlfile)


if __name__ == "__main__":
    print("Running local handler...")
    local_handler()
