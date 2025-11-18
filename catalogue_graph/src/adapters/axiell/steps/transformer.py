"""Transformer step for the Axiell adapter.

Loads records for a changeset from Iceberg, applies a dummy transform, and
indexes the documents into Elasticsearch.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from typing import Any, cast

import elasticsearch.helpers
import pyarrow as pa
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from adapters.axiell import config
from adapters.axiell.models import AxiellAdapterTransformerEvent, TransformResult
from adapters.axiell.steps.loader import AXIELL_NAMESPACE
from adapters.axiell.table_config import get_iceberg_table
from adapters.utils.iceberg import IcebergTableClient
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name


class TransformerConfig(BaseModel):
    use_rest_api_table: bool = True
    es_mode: ElasticsearchMode = "private"


@dataclass
class TransformerRuntime:
    table_client: IcebergTableClient
    es_client: Elasticsearch
    index_name: str


def build_runtime(config_obj: TransformerConfig | None = None) -> TransformerRuntime:
    cfg = config_obj or TransformerConfig(
        es_mode=cast(ElasticsearchMode, config.ES_MODE)
    )
    table = get_iceberg_table(use_rest_api_table=cfg.use_rest_api_table)
    table_client = IcebergTableClient(table, default_namespace=AXIELL_NAMESPACE)
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


def _rows_for_changeset(runtime: TransformerRuntime, changeset_id: str) -> pa.Table:
    return runtime.table_client.get_records_by_changeset(changeset_id)


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
    table = _rows_for_changeset(runtime, request.changeset_id)
    documents = _transform_rows(table)
    indexed, errors = _index_documents(runtime, documents)
    return TransformResult(
        changeset_id=request.changeset_id,
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
        type=str,
        required=True,
        help="Changeset identifier to transform",
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
    args = parser.parse_args()
    runtime = build_runtime(
        TransformerConfig(
            use_rest_api_table=args.use_rest_api_table, es_mode=args.es_mode
        )
    )
    event = AxiellAdapterTransformerEvent(changeset_id=args.changeset_id, job_id=args.job_id)
    response = handler(event, runtime=runtime)
    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
