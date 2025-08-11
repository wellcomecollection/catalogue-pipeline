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
from table_config import get_glue_table, get_local_table

XMLPARSER = etree.XMLParser(remove_blank_text=True)
EBSCO_NAMESPACE = "ebsco"


class EbscoAdapterLoaderConfig(BaseModel):
    is_local: bool = False
    use_glue_table: bool = True


class EbscoAdapterLoaderEvent(BaseModel):
    s3_location: str
    job_id: str


class EbscoAdapterLoaderResult(BaseModel):
    snapshot_id: str | None = None


def update_from_xml_file(table: IcebergTable, xmlfile: IO[bytes]) -> str | None:
    return update_from_xml(table, load_xml(xmlfile))


def load_xml(xmlfile: IO[bytes]) -> etree._Element:
    return etree.parse(xmlfile, parser=XMLPARSER).getroot()


def update_from_xml(table: IcebergTable, collection: etree._Element) -> str | None:
    records = nodes_to_records(collection)
    return update_table(
        table,
        data_to_pa_table(
            records
        ),  # [node_to_record(record_node) for record_node in collection]),
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
) -> EbscoAdapterLoaderResult:
    print(f"Running handler with config: {config_obj}")
    print(f"Processing event: {event}")

    try:
        if config_obj.use_glue_table and not config_obj.is_local:
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

        with smart_open.open(event.s3_location, "rb") as f:
            snapshot_id = update_from_xml_file(table, f)

        return EbscoAdapterLoaderResult(snapshot_id=snapshot_id)
    except Exception as e:
        print(f"Error processing event: {e}")
        return EbscoAdapterLoaderResult()


def lambda_handler(event: EbscoAdapterLoaderEvent, context: Any) -> dict[str, Any]:
    try:
        return handler(
            EbscoAdapterLoaderEvent.model_validate(event), EbscoAdapterLoaderConfig()
        ).model_dump()
    except Exception:
        return EbscoAdapterLoaderResult().model_dump()


def local_handler() -> EbscoAdapterLoaderResult:
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
    parser.add_argument(
        "--local",
        action="store_true",
        help="Run locally without AWS dependencies",
    )

    args = parser.parse_args()

    event = EbscoAdapterLoaderEvent(s3_location=args.xmlfile)
    config_obj = EbscoAdapterLoaderConfig(
        is_local=args.local, use_glue_table=args.use_glue_table
    )

    return handler(event=event, config_obj=config_obj)


def main() -> None:
    """Entry point for the loader script"""
    print("Running local handler...")
    result = local_handler()
    print(f"Result: {result}")
    if not result.snapshot_id:
        sys.exit(1)


if __name__ == "__main__":
    main()
