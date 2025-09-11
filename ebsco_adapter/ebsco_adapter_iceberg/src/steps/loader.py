"""Loader step for the EBSCO adapter.

Reads a MARC XML file (local path or S3 URI), extracts record IDs + raw XML,
and upserts them into the Iceberg table (Glue or local). Returns a changeset
identifier used by the transformer; skips work if the file was already loaded.
"""

import argparse
from datetime import datetime
from typing import IO, Any, cast

import pyarrow as pa
import smart_open
from lxml import etree
from pydantic import BaseModel
from pyiceberg.table import Table as IcebergTable

import config
from models.step_events import (
    EbscoAdapterLoaderEvent,
    EbscoAdapterTransformerEvent,
)
from schemata import ARROW_SCHEMA
from table_config import get_local_table, get_rest_api_table
from utils.iceberg import IcebergTableClient
from utils.tracking import is_file_already_processed, record_processed_file

XMLPARSER = etree.XMLParser(remove_blank_text=True)
EBSCO_NAMESPACE = "ebsco"


class EbscoAdapterLoaderConfig(BaseModel):
    use_rest_api_table: bool = True


def update_from_xml_file(table: IcebergTable, xmlfile: IO[bytes]) -> str | None:
    return update_from_xml(table, load_xml(xmlfile))


def load_xml(xmlfile: IO[bytes]) -> etree._Element:
    return etree.parse(xmlfile, parser=XMLPARSER).getroot()


def update_from_xml(table: IcebergTable, collection: etree._Element) -> str | None:
    records = nodes_to_records(collection)
    updater = IcebergTableClient(table, default_namespace=EBSCO_NAMESPACE)
    return updater.update(data_to_pa_table(records))


def nodes_to_records(collection: etree._Element) -> list[dict[str, str]]:
    return [node_to_record(record_node) for record_node in collection]


def node_to_record(node: etree._Element) -> dict[str, str]:
    controlfield = cast(
        list[str], node.xpath("./*[local-name()='controlfield' and @tag='001']/text()")
    )
    if not controlfield:
        raise Exception("Could not find controlfield with tag 001")
    record_id: str = controlfield[0]
    # serialize XML
    content = etree.tostring(node, encoding="unicode", pretty_print=False)
    return {"id": record_id, "content": content}


def data_to_pa_table(data: list[dict[str, str]]) -> pa.Table:
    namespaced = [{"namespace": EBSCO_NAMESPACE, **row} for row in data]
    return pa.Table.from_pylist(namespaced, schema=ARROW_SCHEMA)


def handler(
    event: EbscoAdapterLoaderEvent, config_obj: EbscoAdapterLoaderConfig
) -> EbscoAdapterTransformerEvent:
    print(f"Running handler with config: {config_obj}")
    print(f"Processing event: {event}")

    prior_record = is_file_already_processed(event.file_location, step="loaded")
    if prior_record:
        print(
            "Source file previously processed; skipping loader work and forwarding prior changeset_id"
        )
        prior_changeset = prior_record.get("changeset_id")
        return EbscoAdapterTransformerEvent(
            changeset_id=prior_changeset,
            job_id=event.job_id,
            index_date=event.index_date,
            file_location=event.file_location,
        )

    if config_obj.use_rest_api_table:
        print("Using S3 Tables Iceberg REST API table...")
        table = get_rest_api_table(
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            table_name=config.GLUE_TABLE_NAME,
            namespace=config.GLUE_NAMESPACE,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
            create_if_not_exists=True,
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

    # Record the processed file to S3
    record_processed_file(
        job_id=event.job_id,
        file_location=event.file_location,
        step="loaded",
        payload_obj=EbscoAdapterTransformerEvent(
            changeset_id=changeset_id,
            job_id=event.job_id,
            index_date=event.index_date,
            file_location=event.file_location,
        ),
    )

    return EbscoAdapterTransformerEvent(
        changeset_id=changeset_id,
        job_id=event.job_id,
        index_date=event.index_date,
        file_location=event.file_location,
    )


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
        "--use-rest-api-table",
        action="store_true",
        help="Use S3 Tables Iceberg REST API table instead of local table",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Optional job id (defaults to current time if omitted)",
    )

    args = parser.parse_args()

    job_id = args.job_id or datetime.now().strftime("%Y%m%dT%H%M")

    event = EbscoAdapterLoaderEvent(file_location=args.xmlfile, job_id=job_id)
    use_rest_api = args.use_rest_api_table
    config_obj = EbscoAdapterLoaderConfig(use_rest_api_table=use_rest_api)

    return handler(event=event, config_obj=config_obj)


def main() -> None:
    print("Running loader handler...")
    try:
        local_handler()

    except Exception as exc:  # surface failures clearly in local runs
        print(f"Loader failed: {exc}")
        raise


if __name__ == "__main__":
    main()
