import argparse
import sys
from typing import IO, Any

import pyarrow as pa
import smart_open
from lxml import etree
from pydantic import BaseModel
from pyiceberg.table import Table as IcebergTable

import config
from iceberg_updates import update_table
from schemata import ARROW_SCHEMA
from steps.transformer import EbscoAdapterTransformerEvent
from table_config import get_glue_table, get_local_table
from utils.tracking import record_processed_file, is_file_already_processed

XMLPARSER = etree.XMLParser(remove_blank_text=True)
EBSCO_NAMESPACE = "ebsco"


class EbscoAdapterLoaderConfig(BaseModel):
    use_glue_table: bool = True


class EbscoAdapterLoaderEvent(BaseModel):
    file_location: str


def update_from_xml_file(table: IcebergTable, xmlfile: IO[bytes]) -> str | None:
    return update_from_xml(table, load_xml(xmlfile))


def load_xml(xmlfile: IO[bytes]) -> etree._Element:
    return etree.parse(xmlfile, parser=XMLPARSER).getroot()


def update_from_xml(table: IcebergTable, collection: etree._Element) -> str | None:
    records = nodes_to_records(collection)
    return update_table(
        table,
        data_to_pa_table(records),
        EBSCO_NAMESPACE,
    )


def nodes_to_records(collection: etree._Element) -> list[dict[str, str]]:
    return [node_to_record(record_node) for record_node in collection]


def node_to_record(node: etree._Element) -> dict[str, str]:
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


def data_to_pa_table(data: list[dict[str, str]]) -> pa.Table:
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def handler(
    event: EbscoAdapterLoaderEvent, config_obj: EbscoAdapterLoaderConfig
) -> EbscoAdapterTransformerEvent:
    print(f"Running handler with config: {config_obj}")
    print(f"Processing event: {event}")

    # Check if this file has already been processed
    if is_file_already_processed(event.file_location):
        print(f"File {event.file_location} has already been processed, skipping...")
        return EbscoAdapterTransformerEvent(changeset_id=None)

    if config_obj.use_glue_table:
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

    with smart_open.open(event.file_location, "rb") as f:
        changeset_id = update_from_xml_file(table, f)

    # Record the processed file to S3 if processing was successful
    if changeset_id:
        record_processed_file(event.file_location)

    return EbscoAdapterTransformerEvent(changeset_id=changeset_id)


def lambda_handler(event: EbscoAdapterLoaderEvent, context: Any) -> dict[str, Any]:
    return handler(
        EbscoAdapterLoaderEvent.model_validate(event), EbscoAdapterLoaderConfig()
    ).model_dump()


def local_handler() -> EbscoAdapterTransformerEvent:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument(
        "xmlfile",
        type=str,
        help="Path to the XML file to process (supports local paths and S3 URLs)",
    )
    parser.add_argument(
        "--use-glue-table",
        action="store_true",
        help="Use AWS Glue table instead of local table",
    )

    args = parser.parse_args()

    event = EbscoAdapterLoaderEvent(file_location=args.xmlfile)
    config_obj = EbscoAdapterLoaderConfig(use_glue_table=args.use_glue_table)

    return handler(event=event, config_obj=config_obj)


def main() -> None:
    """Entry point for the loader script"""
    print("Running local handler...")
    result = local_handler()
    print(f"Result: {result}")
    if not result.changeset_id:
        sys.exit(1)


if __name__ == "__main__":
    main()
