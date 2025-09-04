"""
Transformer step for EBSCO adapter pipeline.

Transforms data from the loader output into the final format for downstream
processing (currently indexing into Elasticsearch).
"""

import argparse
import io
from collections.abc import Generator, Iterable
from typing import Any

import elasticsearch.helpers
import pyarrow as pa
from elasticsearch import Elasticsearch
from pydantic import BaseModel
from pymarc import parse_xml_to_array

import config
from models.marc import MarcRecord
from models.work import BaseWork, DeletedWork, SourceWork
from table_config import get_glue_table, get_local_table
from utils.elasticsearch import get_client, get_standard_index_name
from utils.iceberg import IcebergTableClient

# Batch size for converting Arrow tables to Python objects before indexing
BATCH_SIZE = 10_000


class EbscoAdapterTransformerConfig(BaseModel):
    is_local: bool = False
    use_glue_table: bool = True
    pipeline_date: str
    index_date: str | None = None


class EbscoAdapterTransformerEvent(BaseModel):
    changeset_id: str | None = None
    index_date: str | None = None
    job_id: str  # required


class EbscoAdapterTransformerResult(BaseModel):
    """Result of transformer execution.

    batches contains one entry per processed RecordBatch; each inner list is the
    ordered list of IDs successfully transformed & sent for indexing for that batch.
    An empty result (no changeset or no valid records) yields batches=[].
    """

    batches: list[list[str]] = []
    success_count: int = 0
    failure_count: int = 0


def transform(work_id: str, content: str) -> list[SourceWork]:
    """Parse a MARC XML string into TransformedWork records."""

    def valid_record(record: Any) -> bool:  # local for clarity
        return bool(record and getattr(record, "title", None))

    try:
        marc_records: list[MarcRecord] = parse_xml_to_array(io.StringIO(content))
    except Exception as exc:  # broad, but we want to skip bad MARC payloads only
        print(f"Failed to parse MARC content for id={work_id}: {exc}")
        return []

    return [
        SourceWork(id=work_id, title=str(record.title))
        for record in marc_records
        if valid_record(record)
    ]


def _generate_actions(
    records: Iterable[BaseWork],  # accept any pydantic model with model_dump
    index_name: str,
) -> Generator[dict[str, Any], None, None]:
    for record in records:
        yield {
            "_index": index_name,
            "_id": record.id,
            "_source": record.model_dump(),
        }


def load_data(
    elastic_client: Elasticsearch, records: Iterable[BaseWork], index_name: str
) -> tuple[int, int]:
    """Index records; return (success_count, error_count)."""
    success_count, errors = elasticsearch.helpers.bulk(
        elastic_client,
        _generate_actions(records, index_name),
        raise_on_error=False,
        stats_only=False,
    )

    # ES8 helper returns a list of error items when raise_on_error=False; may be empty
    error_items: list[Any]
    error_items = [] if not errors or isinstance(errors, int) else list(errors)
    error_count = len(error_items)

    if error_items:
        printed: list[str] = []
        for e in error_items[:5]:
            try:
                idx_error = e.get("index", {})
                printed.append(
                    f"id={idx_error.get('_id')} status={idx_error.get('status')} error={idx_error.get('error', {}).get('type')}"
                )
            except Exception:  # pragma: no cover - defensive
                printed.append(str(e)[:200])
        print(
            f"Encountered {error_count} indexing errors (showing up to 5):\n"
            + "\n".join(printed)
        )
    else:
        print(
            f"Successfully stored {success_count} transformed records in index: {index_name}"
        )

    return success_count, error_count


def _process_batch(
    batch: pa.RecordBatch,
    index_name: str,
    es_client: Elasticsearch,
) -> tuple[int, int, list[str]]:
    """Process a single Arrow RecordBatch; return (success_count, failure_count, ids).

    Empty content denotes a deletion; in that case we index a DeletedWork with a
    standard reason so downstream consumers know the record was removed at source.
    """
    print(f"Processing batch with {len(batch)} rows")

    transformed: list[BaseWork] = []

    # Schema (see schemata.py): namespace, id, content, changeset, last_modified
    for row in batch.to_pylist():
        work_id = row["id"]  # guaranteed by schema
        content = row.get("content")  # optional per schema
        if content:
            transformed.extend(transform(str(work_id), content))
        else:
            # Create a DeletedWork marker document
            transformed.append(
                DeletedWork(
                    id=str(work_id),
                    deleted_reason="not found in EBSCO source",
                )
            )

    if not transformed:
        print("No valid records produced from batch")
        return 0, 0, []

    success, failed = load_data(es_client, transformed, index_name)
    ids = [t.id for t in transformed]
    return success, failed, ids


def handler(
    event: EbscoAdapterTransformerEvent, config_obj: EbscoAdapterTransformerConfig
) -> EbscoAdapterTransformerResult:
    print(f"Running transformer handler with config: {config_obj}")
    print(f"Processing event: {event}")
    print(f"Received job_id: {event.job_id}")

    if event.changeset_id is None:
        print("No changeset_id provided, skipping transformation.")
        return EbscoAdapterTransformerResult()

    changeset_id = event.changeset_id
    print(f"Processing loader output with changeset_id: {changeset_id}")

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

    table_client = IcebergTableClient(table)

    pa_table: pa.Table = table_client.get_records_by_changeset(changeset_id)
    print(f"Retrieved {len(pa_table)} records from table")

    es_client = get_client(
        pipeline_date=config_obj.pipeline_date,
        is_local=config_obj.is_local,
        api_key_name="transformer-ebsco-test",
    )
    index_name = get_standard_index_name(
        "works-source",
        (
            # Cascading choice for date in index name
            event.index_date or config_obj.index_date or config_obj.pipeline_date
        ),
    )

    print(
        f"Writing to Elasticsearch index: {index_name} in pipeline {config_obj.pipeline_date} ..."
    )

    total_success = 0
    total_failed = 0
    batches_ids: list[list[str]] = []

    for batch in pa_table.to_batches(max_chunksize=BATCH_SIZE):
        success, failed, ids = _process_batch(batch, index_name, es_client)
        total_success += success
        total_failed += failed
        if ids:  # only record non-empty batches
            batches_ids.append(ids)

    result = EbscoAdapterTransformerResult(
        batches=batches_ids, success_count=total_success, failure_count=total_failed
    )

    total_batches = len(result.batches)
    total_ids = sum(len(b) for b in result.batches)
    print(
        f"Transformer summary: batches={total_batches} total_ids={total_ids} success={total_success} failures={total_failed}"
    )

    # No longer raise on failures; caller can inspect failure_count
    return result


def lambda_handler(event: EbscoAdapterTransformerEvent, context: Any) -> dict[str, Any]:
    return handler(
        EbscoAdapterTransformerEvent.model_validate(event),
        EbscoAdapterTransformerConfig(
            pipeline_date=config.PIPELINE_DATE,
            index_date=config.INDEX_DATE,
        ),
    ).model_dump()


def local_handler() -> EbscoAdapterTransformerResult:
    parser = argparse.ArgumentParser(description="Transform EBSCO adapter data")
    parser.add_argument(
        "--changeset-id", type=str, help="Changeset ID from loader output to transform"
    )
    parser.add_argument(
        "--use-glue-table",
        action="store_true",
        help="Use AWS Glue table instead of local table",
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline date being ingested to (defaults to dev).",
        default="dev",
        required=False,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The index date to use (defaults to pipeline date).",
        required=False,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="Job identifier (defaults to 'dev' when not supplied).",
        default="dev",
        required=False,
    )

    args = parser.parse_args()

    event = EbscoAdapterTransformerEvent(
        changeset_id=args.changeset_id, job_id=args.job_id
    )
    config_obj = EbscoAdapterTransformerConfig(
        is_local=True,
        use_glue_table=args.use_glue_table,
        pipeline_date=args.pipeline_date,
        index_date=args.index_date,
    )

    return handler(event=event, config_obj=config_obj)


def main() -> None:  # pragma: no cover - CLI entry point
    print("Running local transformer handler...")
    try:
        local_handler()

    except Exception as exc:  # surface failures clearly in local runs
        print(f"Transformer failed: {exc}")
        raise


if __name__ == "__main__":  # pragma: no cover
    main()
