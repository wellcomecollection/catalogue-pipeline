"""Transformer step for the Axiell adapter.

Loads records for a changeset from Iceberg, applies a dummy transform, and
indexes the documents into Elasticsearch.
"""

from __future__ import annotations

import argparse
import json
from collections.abc import Sequence
from typing import Any, cast

import elasticsearch.helpers
import pyarrow as pa
from elasticsearch import Elasticsearch
from pydantic import BaseModel, ConfigDict, Field

from adapters.axiell import config, helpers
from adapters.axiell.models.step_events import AxiellAdapterTransformerEvent
from adapters.axiell.steps.loader import AXIELL_NAMESPACE
from adapters.utils.adapter_store import AdapterStore
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name


class AxiellAdapterTransformerConfig(BaseModel):
    use_rest_api_table: bool = True
    create_if_not_exists: bool = False
    es_mode: ElasticsearchMode = "private"


class TransformResult(BaseModel):
    changeset_ids: list[str] = Field(default_factory=list)
    indexed: int
    errors: list[str]
    job_id: str | None = None


class TransformerRuntime(BaseModel):
    table_client: AdapterStore
    es_client: Elasticsearch
    index_name: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def build_runtime(
    config_obj: AxiellAdapterTransformerConfig | None = None,
) -> TransformerRuntime:
    cfg = config_obj or AxiellAdapterTransformerConfig(
        es_mode=cast(ElasticsearchMode, config.ES_MODE)
    )
    table = helpers.build_adapter_table(
        use_rest_api_table=cfg.use_rest_api_table,
        create_if_not_exists=cfg.create_if_not_exists,
    )
    table_client = AdapterStore(table, default_namespace=AXIELL_NAMESPACE)
    es_client = get_client(
        api_key_name=config.ES_API_KEY_NAME,
        pipeline_date=config.PIPELINE_DATE,
        es_mode=cfg.es_mode,
    )
    index_name = get_standard_index_name(
        config.ES_INDEX_NAME, config.INDEX_DATE or config.PIPELINE_DATE
    )
    return TransformerRuntime(
        table_client=table_client, es_client=es_client, index_name=index_name
    )


def _rows_for_changesets(
    runtime: TransformerRuntime, changeset_ids: Sequence[str]
) -> pa.Table:
    tables = [
        runtime.table_client.get_records_by_changeset(changeset_id)
        for changeset_id in changeset_ids
    ]
    materialized = [
        table for table in tables if table is not None and table.num_rows > 0
    ]
    if not materialized:
        return pa.Table.from_pylist([])
    if len(materialized) == 1:
        return materialized[0]
    return pa.concat_tables(materialized)


def _dummy_document(row: dict[str, Any]) -> dict[str, Any] | None:
    record_id = row.get("id")
    if not record_id:
        return None
    title = f"Axiell record {record_id}"
    return {
        "id": record_id,
        "namespace": row.get("namespace", AXIELL_NAMESPACE),
        "title": title,
        "content": row.get("content"),
    }


def _transform_rows(table: pa.Table) -> list[dict[str, Any]]:
    if table.num_rows == 0:
        return []
    rows = table.to_pylist()
    docs: list[dict[str, Any]] = []
    for row in rows:
        doc = _dummy_document(row)
        if doc is not None:
            docs.append(doc)
    return docs


def _index_documents(
    runtime: TransformerRuntime, documents: list[dict[str, Any]]
) -> tuple[int, list[str]]:
    if not documents:
        return 0, []
    actions = [
        {"_index": runtime.index_name, "_id": doc["id"], "_source": doc}
        for doc in documents
    ]
    success_count, errors = elasticsearch.helpers.bulk(
        runtime.es_client,
        actions,
        raise_on_error=False,
        stats_only=False,
    )
    normalized_errors: list[str] = []
    if errors and not isinstance(errors, int):
        for err in errors:
            idx_err = err.get("index", {})
            normalized_errors.append(
                f"id={idx_err.get('_id')} status={idx_err.get('status')}"
            )
    return success_count, normalized_errors


def execute_transform(
    request: AxiellAdapterTransformerEvent,
    runtime: TransformerRuntime | None = None,
) -> TransformResult:
    runtime = runtime or build_runtime()
    table = _rows_for_changesets(runtime, request.changeset_ids)
    documents = _transform_rows(table)
    indexed, errors = _index_documents(runtime, documents)
    return TransformResult(
        changeset_ids=request.changeset_ids,
        indexed=indexed,
        errors=errors,
        job_id=request.job_id,
    )


def handler(
    event: AxiellAdapterTransformerEvent, runtime: TransformerRuntime | None = None
) -> TransformResult:
    return execute_transform(event, runtime=runtime)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    request = AxiellAdapterTransformerEvent.model_validate(event)
    response = handler(request)
    return response.model_dump(mode="json")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the Axiell transformer step locally"
    )
    parser.add_argument(
        "--changeset-id",
        dest="changeset_ids",
        action="append",
        required=True,
        help="Changeset identifier to transform (repeatable)",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="Optional job identifier propagated from the trigger",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use S3 Tables (default) instead of the local catalog",
    )
    parser.add_argument(
        "--es-mode",
        default=config.ES_MODE,
        choices=["private", "public", "local"],
        help="Elasticsearch connectivity mode",
    )
    parser.add_argument(
        "--create-if-not-exists",
        action="store_true",
        help="Create the Iceberg table if it does not already exist",
    )

    args = parser.parse_args()
    runtime = build_runtime(
        AxiellAdapterTransformerConfig(
            use_rest_api_table=args.use_rest_api_table,
            create_if_not_exists=args.create_if_not_exists,
            es_mode=args.es_mode,
        )
    )
    event = AxiellAdapterTransformerEvent(
        changeset_ids=args.changeset_ids,
        job_id=args.job_id or args.changeset_ids[0],
    )
    response = handler(event, runtime=runtime)
    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
