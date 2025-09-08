"""
Transformer step for EBSCO adapter pipeline.

Transforms data from the loader output into the final format for downstream
processing (currently indexing into Elasticsearch).
"""

import argparse
import io
import json
from collections.abc import Generator, Iterable
from typing import Any

import elasticsearch.helpers
import pyarrow as pa
import smart_open
from elasticsearch import Elasticsearch
from pydantic import BaseModel
from pymarc import parse_xml_to_array

import config
from models.marc import MarcRecord
from models.step_events import (
    EbscoAdapterTransformerEvent,
    EbscoAdapterTransformerResult,
)
from models.work import BaseWork, DeletedWork, SourceWork
from table_config import get_glue_table, get_local_table
from utils.elasticsearch import get_client, get_standard_index_name
from utils.iceberg import IcebergTableClient
from utils.tracking import is_file_already_processed, record_processed_file

# Batch size for converting Arrow tables to Python objects before indexing
BATCH_SIZE = 10_000


class EbscoAdapterTransformerConfig(BaseModel):
    is_local: bool = False
    use_glue_table: bool = True
    pipeline_date: str
    index_date: str | None = None


def _write_batch_file(batches_ids: list[list[str]], job_id: str) -> str | None:
    """Persist batch id lists to S3 and return the S3 URI, or None on failure.

    The file format is a JSON array of arrays (each inner list is the ordered
    sequence of work IDs for a processed Arrow RecordBatch). We keep this
    separate from the step result to avoid large payloads in state transitions.
    """
    if not batches_ids:
        return None

    batch_file_location = (
        f"s3://{config.S3_BUCKET}/{config.S3_PREFIX.rstrip('/')}/{job_id}.json"
    )
    try:
        with smart_open.open(batch_file_location, "w", encoding="utf-8") as f:
            f.write(json.dumps(batches_ids))
        print(f"Wrote batch id file to {batch_file_location}")
        return batch_file_location
    except Exception as exc:  # pragma: no cover - defensive logging only
        print(f"Failed to write batch id file {batch_file_location}: {exc}")
        return None


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

    # Short-circuit if this file has already been transformed (mirrors loader behaviour)
    if event.file_location:
        prior_record = is_file_already_processed(
            event.file_location, step="transformed"
        )
        if prior_record:
            print(
                "Source file previously transformed; skipping transformer work (idempotent short-circuit)."
            )
            prior_batch = prior_record.get("batch_file_location")
            idx_date = (
                event.index_date or config_obj.index_date or config_obj.pipeline_date
            )
            return EbscoAdapterTransformerResult(
                index_date=idx_date,
                job_id=event.job_id,
                batch_file_location=prior_batch,
                success_count=0,
                failure_count=0,
            )

    # Perform a reindex when no changeset is supplied
    full_retransform = event.changeset_id is None
    if full_retransform:
        print("No changeset_id provided; performing full reindex of records.")
    else:
        print(f"Processing loader output with changeset_id: {event.changeset_id}")

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

    if full_retransform:
        pa_table = table_client.get_all_records()
        print(f"Retrieved {len(pa_table)} records from table for reindex")
    else:
        pa_table = table_client.get_records_by_changeset(event.changeset_id)  # type: ignore[arg-type]
        print(
            f"Retrieved {len(pa_table)} records from table for changeset {event.changeset_id}"
        )

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

    # Persist batch ids (if any) and get location for downstream step
    batch_file_location = _write_batch_file(batches_ids, event.job_id)

    total_batches = len(batches_ids)
    total_ids = sum(len(b) for b in batches_ids)
    print(
        f"Transformer summary: batches={total_batches} total_ids={total_ids} success={total_success} failures={total_failed}"
    )

    idx_date = event.index_date or config_obj.index_date or config_obj.pipeline_date
    result = EbscoAdapterTransformerResult(
        index_date=idx_date,
        job_id=event.job_id,
        batch_file_location=batch_file_location,
        success_count=total_success,
        failure_count=total_failed,
    )

    if event.file_location:
        record_processed_file(
            job_id=event.job_id,
            file_location=event.file_location,
            step="transformed",
            payload_obj=result,
        )

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
        changeset_id=args.changeset_id, job_id=args.job_id, file_location=None
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
