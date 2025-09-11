"""Transformer step for the EBSCO adapter.

Converts Iceberg loader output (all records or a changeset) into `SourceWork` /
`DeletedWork` documents and indexes them into Elasticsearch, writing a batch
ID manifest for downstream consumers; idempotent on previously processed files.
"""

import argparse
import io
import json
from collections.abc import Generator, Iterable
from pathlib import PurePosixPath
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
from utils.elasticsearch import get_client, get_standard_index_name
from utils.iceberg import IcebergTableClient, get_iceberg_table
from utils.tracking import is_file_already_processed, record_processed_file

# Batch size for converting Arrow tables to Python objects before indexing
BATCH_SIZE = 10_000


class EbscoAdapterTransformerConfig(BaseModel):
    is_local: bool = False
    use_rest_api_table: bool = True
    pipeline_date: str
    index_date: str | None = None


def _write_batch_file(
    batches_ids: list[list[str]], job_id: str, *, changeset_id: str | None
) -> tuple[str, str, str]:
    # Build the S3 key using PurePosixPath for clean joining (no leading // issues)
    # Prefer .ndjson to signal line-delimited JSON (supported by Step Functions)
    file_name = f"{changeset_id or 'reindex'}.{job_id}.ids.ndjson"
    key = PurePosixPath(config.BATCH_S3_PREFIX) / file_name
    bucket = config.S3_BUCKET
    batch_file_location = f"s3://{bucket}/{key.as_posix()}"

    with smart_open.open(batch_file_location, "w", encoding="utf-8") as f:
        # Write NDJSON: one JSON object per line -> one Distributed Map item per line
        # Line shape: {"ids": ["id1", "id2", ...]}
        for ids in batches_ids:
            f.write(json.dumps({"ids": ids}) + "\n")

    print(f"Wrote batch id file to {batch_file_location}")
    return batch_file_location, bucket, key.as_posix()


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


def _record_transformed_file(
    event: EbscoAdapterTransformerEvent, result: EbscoAdapterTransformerResult
) -> None:
    """Record that a source file was transformed (idempotency tracking).

    Separated from the main handler for clarity & easier testing.
    Safe no-op when no file_location is supplied (e.g. local runs without S3 input).
    """
    if not event.file_location:
        print("No file location provided for tracking, skipping!")
        return

    record_processed_file(
        job_id=event.job_id,
        file_location=event.file_location,
        step="transformed",
        payload_obj=result,
    )


def process_batches(
    pa_table: pa.Table,
    es_client: Elasticsearch,
    *,
    index_name: str,
    job_id: str,
    changeset_id: str | None,
    index_date: str,
) -> EbscoAdapterTransformerResult:
    """Process an Arrow table into Elasticsearch and return a result object.

    Responsibilities:
    - Iterate over record batches
    - Transform / delete logic via _process_batch
    - Accumulate counts & ids
    - Write batch id manifest file
    - Return EbscoAdapterTransformerResult (tracking is left to caller)
    """
    total_success = 0
    total_failed = 0
    batches_ids: list[list[str]] = []

    for batch in pa_table.to_batches(max_chunksize=BATCH_SIZE):
        success, failed, ids = _process_batch(batch, index_name, es_client)
        total_success += success
        total_failed += failed
        if ids:
            batches_ids.append(ids)

    batch_file_location, batch_file_bucket, batch_file_key = _write_batch_file(
        batches_ids, job_id, changeset_id=changeset_id
    )

    total_batches = len(batches_ids)
    total_ids = sum(len(b) for b in batches_ids)
    print(
        f"Transformer summary: batches={total_batches} total_ids={total_ids} success={total_success} failures={total_failed}"
    )

    return EbscoAdapterTransformerResult(
        index_date=index_date,
        job_id=job_id,
        batch_file_location=batch_file_location,
        batch_file_bucket=batch_file_bucket,
        batch_file_key=batch_file_key,
        success_count=total_success,
        failure_count=total_failed,
    )


def handler(
    event: EbscoAdapterTransformerEvent, config_obj: EbscoAdapterTransformerConfig
) -> EbscoAdapterTransformerResult:
    print(f"Running transformer handler with config: {config_obj}")
    print(f"Processing event: {event}")
    print(f"Received job_id: {event.job_id}")

    # Determine index date by cascading choice: event > config > pipeline_date
    index_date = event.index_date or config_obj.index_date or config_obj.pipeline_date

    prior_record = None
    if event.file_location:
        prior_record = is_file_already_processed(
            event.file_location, step="transformed"
        )

    # Short-circuit if this file has already been transformed (mirrors loader behaviour)
    if prior_record:
        print(
            "Source file previously transformed; skipping transformer work (idempotent short-circuit)."
        )
        prior_batch = prior_record.get("batch_file_location")
        prior_bucket = prior_record.get("batch_file_bucket")
        prior_key = prior_record.get("batch_file_key")

        return EbscoAdapterTransformerResult(
            index_date=index_date,
            job_id=event.job_id,
            batch_file_location=prior_batch,
            batch_file_bucket=prior_bucket,
            batch_file_key=prior_key,
            success_count=0,
            failure_count=0,
        )

    table = get_iceberg_table(config_obj.use_rest_api_table)
    table_client = IcebergTableClient(table)

    # Perform a reindex when no changeset is supplied
    if event.changeset_id is None:
        print("No changeset_id provided; performing full reindex of records.")
        pa_table = table_client.get_all_records()
    else:
        print(f"Processing loader output with changeset_id: {event.changeset_id}")
        pa_table = table_client.get_records_by_changeset(event.changeset_id)  # type: ignore[arg-type]

    print(f"Retrieved {len(pa_table)} records from table for processing")

    es_client = get_client(
        pipeline_date=config_obj.pipeline_date,
        is_local=config_obj.is_local,
        api_key_name="transformer-ebsco-test",
    )
    index_name = get_standard_index_name("works-source", index_date)

    print(
        f"Writing to Elasticsearch index: {index_name} in pipeline {config_obj.pipeline_date} ..."
    )

    result = process_batches(
        pa_table,
        es_client,
        index_name=index_name,
        job_id=event.job_id,
        changeset_id=event.changeset_id,
        index_date=index_date,
    )
    _record_transformed_file(event, result)

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
        "--use-rest-api-table",
        action="store_true",
        help="Use S3 Tables Iceberg REST API table instead of local table",
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
    use_rest_api = args.use_rest_api_table
    config_obj = EbscoAdapterTransformerConfig(
        is_local=True,
        use_rest_api_table=use_rest_api,
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
