"""Transformer step for the EBSCO adapter.

Converts Iceberg loader output (all records or a changeset) into `SourceWork` /
`DeletedWork` documents and indexes them into Elasticsearch, writing a batch
ID manifest for downstream consumers.
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
from pymarc.record import Record as MarcRecord

from adapters.ebsco import config
from adapters.ebsco.manifests import ManifestWriter
from adapters.ebsco.models.manifests import ErrorLine, TransformerManifest
from adapters.ebsco.models.step_events import (
    EbscoAdapterTransformerEvent,
)
from adapters.ebsco.models.work import (
    BaseWork,
    DeletedWork,
    SourceIdentifier,
    SourceWork,
)
from adapters.ebsco.transformers.ebsco_to_weco import (
    EBSCO_IDENTIFIER_TYPE,
    transform_record,
)
from adapters.ebsco.utils.iceberg import IcebergTableClient, get_iceberg_table
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name

# Batch size for converting Arrow tables to Python objects before indexing
# This must result in batches of output ids that fit in the 256kb item
# limit for step function invocations (with some margin).
BATCH_SIZE = 5_000


class EbscoAdapterTransformerConfig(BaseModel):
    es_mode: ElasticsearchMode = "private"
    use_rest_api_table: bool = True
    pipeline_date: str
    # Optional override for index naming. When None we use pipeline_date.
    index_date: str | None = None


def _to_error_line(err: dict[str, Any]) -> ErrorLine | None:
    """Convert a raw error dict into an ErrorLine.

    The message is a deterministic '; ' joined list of key=value pairs (excluding 'id')
    sorted by key name. None values are skipped. Returns None if no id present.
    """
    rec_id = err.get("id")
    if not rec_id:
        return None
    parts: list[str] = []
    for k in sorted(err.keys()):
        if k == "id":
            continue
        v = err[k]
        if v is None:
            continue
        parts.append(f"{k}={v}")
    message = "; ".join(parts)
    return ErrorLine(id=str(rec_id), message=message)


def transform(
    work_id: str, content: str
) -> tuple[list[SourceWork], list[dict[str, Any]]]:
    """Parse a MARC XML string into SourceWork records.

    Returns (works, errors). Each error is a dict with at minimum stage, id, reason.
    Reasons: empty_content | parse_error | transform_error
    """

    errors: list[dict[str, Any]] = []

    if not content:
        errors.append({"stage": "transform", "id": work_id, "reason": "empty_content"})
        return [], errors

    try:
        marc_records: list[MarcRecord] = parse_xml_to_array(io.StringIO(content))
    except Exception as exc:  # broad, skip bad MARC payloads only
        detail = str(exc)[:200]
        print(f"Failed to parse MARC content for id={work_id}: {detail}")
        errors.append(
            {
                "stage": "transform",
                "id": work_id,
                "reason": "parse_error",
                "detail": detail,
            }
        )
        return [], errors

    # Transform each valid MARC record individually so one bad record doesn't
    # fail the whole payload. Capture and surface transformation errors.
    works: list[SourceWork] = []
    for record in marc_records:
        try:
            works.append(transform_record(record))
        except Exception as exc:  # broad: we want to continue other records
            detail = str(exc)[:200]
            print(f"Failed to transform MARC record for id={work_id}: {exc}")
            errors.append(
                {
                    "stage": "transform",
                    "id": work_id,
                    "reason": "transform_error",
                    "detail": detail,
                }
            )

    return works, errors


def _generate_actions(
    records: Iterable[BaseWork],  # accept any pydantic model with model_dump
    index_name: str,
) -> Generator[dict[str, Any]]:
    for record in records:
        yield {
            "_index": index_name,
            "_id": str(
                record.source_identifier
            ),  # Use formatted string id via SourceIdentifier
            "_source": record.model_dump(),
        }


def load_data(
    elastic_client: Elasticsearch, records: Iterable[BaseWork], index_name: str
) -> tuple[int, list[dict[str, Any]]]:
    """Index records; return (success_count, error_details list).

    error_details elements shaped as {"stage": "index", "id": str, "status": int, "error_type": str}
    """
    success_count, errors = elasticsearch.helpers.bulk(
        elastic_client,
        _generate_actions(records, index_name),
        raise_on_error=False,
        stats_only=False,
    )

    error_items: list[Any] = (
        [] if not errors or isinstance(errors, int) else list(errors)
    )
    error_details: list[dict[str, Any]] = []
    for e in error_items:
        try:
            idx_error = e.get("index", {})
            error_details.append(
                {
                    "stage": "index",
                    "id": idx_error.get("_id"),
                    "status": idx_error.get("status"),
                    "error_type": (idx_error.get("error") or {}).get("type"),
                }
            )
        except Exception:  # pragma: no cover - defensive
            error_details.append({"stage": "index", "raw": str(e)[:500]})

    if error_details:
        preview = [
            f"id={d.get('id')} type={d.get('error_type')}" for d in error_details[:5]
        ]
        print(
            f"Encountered {len(error_details)} indexing errors (showing up to 5):\n"
            + "\n".join(preview)
        )
    else:
        print(
            f"Successfully stored {success_count} transformed records in index: {index_name}"
        )

    return success_count, error_details


def _process_batch(
    batch: pa.RecordBatch,
    index_name: str,
    es_client: Elasticsearch,
) -> tuple[int, list[str], list[dict[str, Any]]]:
    """Process a single Arrow RecordBatch.

    Returns (success_count, ids, errors_list).
    errors_list contains per-record transformation or indexing errors.
    """
    print(f"Processing batch with {len(batch)} rows")

    transformed: list[BaseWork] = []
    errors: list[dict[str, Any]] = []

    for row in batch.to_pylist():
        work_id = str(row["id"])  # guaranteed by schema
        content = row.get("content")
        if content:
            works, t_errors = transform(work_id, content)
            transformed.extend(works)
            if t_errors:
                errors.extend(t_errors)
        else:
            # deletion marker -> DeletedWork counts as success (not an error)
            transformed.append(
                DeletedWork(
                    deleted_reason="not found in EBSCO source",
                    source_identifier=SourceIdentifier(
                        identifier_type=EBSCO_IDENTIFIER_TYPE,
                        ontology_type="Work",
                        value=work_id,
                    ),
                )
            )

    if not transformed and not errors:
        print("No valid records produced from batch")
        return 0, [], errors

    success, index_errors = load_data(es_client, transformed, index_name)
    errors.extend(index_errors)
    ids = [str(t.source_identifier) for t in transformed]
    return success, ids, errors


def process_batches(
    pa_table: pa.Table,
    es_client: Elasticsearch,
    *,
    index_name: str,
    job_id: str,
    changeset_id: str | None,
) -> TransformerManifest:
    """Process an Arrow table into Elasticsearch and return a result object.

    Responsibilities:
    - Iterate over record batches
    - Transform / delete logic via _process_batch
    - Accumulate counts & ids
    - Write batch id manifest file
    - Return EbscoAdapterTransformerResult (tracking is left to caller)
    """
    total_success = 0
    batches_ids: list[list[str]] = []
    all_errors: list[dict[str, Any]] = []

    for batch in pa_table.to_batches(max_chunksize=BATCH_SIZE):
        success, ids, errors = _process_batch(batch, index_name, es_client)
        total_success += success
        if ids:
            batches_ids.append(ids)
        if errors:
            all_errors.extend(errors)

    error_lines: list[ErrorLine] = []
    if all_errors:
        for e in all_errors:
            line = _to_error_line(e)
            if line:
                error_lines.append(line)

    writer = ManifestWriter(
        job_id=job_id,
        changeset_id=changeset_id,
        bucket=config.S3_BUCKET,
        prefix=config.BATCH_S3_PREFIX,
    )
    result_manifest = writer.build_manifest(
        job_id=job_id,
        batches_ids=batches_ids,
        errors=error_lines,
        success_count=total_success,
    )
    total_batches = len(batches_ids)
    total_ids = sum(len(b) for b in batches_ids)
    print(
        f"Transformer summary: batches={total_batches} total_ids={total_ids} success={total_success} failures={len(error_lines)}"
    )
    return result_manifest


def handler(
    event: EbscoAdapterTransformerEvent, config_obj: EbscoAdapterTransformerConfig
) -> TransformerManifest:
    print(f"Running transformer handler with config: {config_obj}")
    print(f"Processing event: {event}")
    print(f"Received job_id: {event.job_id}")

    # Determine index date from config override or fallback to pipeline date
    index_date = config_obj.index_date or config_obj.pipeline_date

    table = get_iceberg_table(config_obj.use_rest_api_table)
    table_client = IcebergTableClient(table)

    # Perform a reindex when no changeset is supplied
    if event.changeset_id is None:
        print("No changeset_id provided; performing full reindex of records.")
        pa_table = table_client.get_all_records()
    else:
        print(f"Processing loader output with changeset_id: {event.changeset_id}")
        pa_table = table_client.get_records_by_changeset(event.changeset_id)

    print(f"Retrieved {len(pa_table)} records from table for processing")

    es_client = get_client(
        pipeline_date=config_obj.pipeline_date,
        es_mode=config_obj.es_mode,
        api_key_name=config.ES_API_KEY_NAME,
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


def local_handler() -> TransformerManifest:
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
        help="Optional index date override (defaults to pipeline date).",
        required=False,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="Job identifier (defaults to 'dev' when not supplied).",
        default="dev",
        required=False,
    )
    parser.add_argument(
        "--es-mode",
        type=str,
        help="Where to index documents. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["local", "public"],
        default="local",
    )

    args = parser.parse_args()

    event = EbscoAdapterTransformerEvent(
        changeset_id=args.changeset_id, job_id=args.job_id
    )
    use_rest_api = args.use_rest_api_table
    config_obj = EbscoAdapterTransformerConfig(
        es_mode=args.es_mode,
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
