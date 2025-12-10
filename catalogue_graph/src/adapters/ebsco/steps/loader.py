"""Loader step for the EBSCO adapter.

Reads a MARC XML file (local path or S3 URI), extracts record IDs + raw XML,
and upserts them into the Iceberg table (Glue or local). Returns a changeset
identifier used by the transformer; skips work if the file was already loaded.
"""

import argparse
import re
from datetime import datetime
from typing import IO, Any, cast

import pyarrow as pa
import smart_open
from lxml import etree
from pydantic import BaseModel
from pyiceberg.table import Table as IcebergTable

from adapters.ebsco import helpers
from adapters.ebsco.models.step_events import EbscoAdapterLoaderEvent
from adapters.ebsco.utils.tracking import (
    ProcessedFileRecord,
)
from adapters.transformers.transformer import TransformerEvent
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA, ARROW_SCHEMA_WITH_TIMESTAMP
from utils.aws import pydantic_from_s3_json

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
    updater = AdapterStore(table, default_namespace=EBSCO_NAMESPACE)
    return updater.snapshot_sync(data_to_pa_table(records))


def nodes_to_records(collection: etree._Element) -> list[dict[str, str]]:
    return [node_to_record(record_node) for record_node in collection]


def node_to_record(node: etree._Element) -> dict[str, str]:
    record_id = extract_record_id(node)
    # serialize XML
    content = etree.tostring(node, encoding="unicode", pretty_print=False)
    return {"id": record_id, "content": content}


def extract_record_id(node: etree._Element) -> str:
    controlfield_values = cast(
        list[str], node.xpath("./*[local-name()='controlfield' and @tag='001']/text()")
    )
    for value in controlfield_values:
        normalized = value.strip()
        if normalized:
            return normalized

    datafield_values = cast(
        list[str],
        node.xpath(
            "./*[local-name()='datafield' and @tag='035']/*[local-name()='subfield' and @code='a']/text()"
        ),
    )
    for raw_value in datafield_values:
        cleaned = _strip_marc_parenthetical_prefix(raw_value)
        if cleaned:
            return cleaned

    raise Exception(
        "Could not find controlfield 001 or usable datafield 035 "
        f"in record: {etree.tostring(node, encoding='unicode', pretty_print=True)}"
    )


def _strip_marc_parenthetical_prefix(raw_value: str) -> str:
    value = raw_value.strip()
    return re.sub(r"^\(.*\)", "", value)


def data_to_pa_table(data: list[dict[str, str]]) -> pa.Table:
    namespaced = [{"namespace": EBSCO_NAMESPACE, **row} for row in data]
    # If the incoming data includes a last_modified field, use the extended schema
    has_timestamp = any("last_modified" in row for row in namespaced)
    schema = ARROW_SCHEMA_WITH_TIMESTAMP if has_timestamp else ARROW_SCHEMA
    return pa.Table.from_pylist(namespaced, schema=schema)


def handler(
    event: EbscoAdapterLoaderEvent, config_obj: EbscoAdapterLoaderConfig
) -> TransformerEvent:
    print(f"Running handler with config: {config_obj}")
    print(f"Processing event: {event}")

    uri = f"{event.file_location}.loaded.json"
    prior_record = pydantic_from_s3_json(ProcessedFileRecord, uri, ignore_missing=True)

    if prior_record:
        print(
            "Source file previously processed; skipping loader work and forwarding prior changeset_id"
        )
        return TransformerEvent.model_validate(prior_record.payload)

    table = helpers.build_adapter_table(config_obj.use_rest_api_table)
    with smart_open.open(event.file_location, "rb") as f:
        changeset_id = update_from_xml_file(table, f)

    # Record the processed file to S3
    payload = TransformerEvent(
        transformer_type="ebsco",
        job_id=event.job_id,
        changeset_ids=[changeset_id] if changeset_id else [],
    )
    record = ProcessedFileRecord(
        job_id=event.job_id, step="loaded", payload=payload.model_dump()
    )
    record.write(event.file_location)

    return TransformerEvent(
        transformer_type="ebsco",
        job_id=event.job_id,
        changeset_ids=[changeset_id] if changeset_id else [],
    )


def lambda_handler(event: EbscoAdapterLoaderEvent, context: Any) -> dict[str, Any]:
    return handler(
        EbscoAdapterLoaderEvent.model_validate(event), EbscoAdapterLoaderConfig()
    ).model_dump()


def local_handler() -> TransformerEvent:
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
    local_handler()


if __name__ == "__main__":
    main()
