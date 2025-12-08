"""Transformer step for the Axiell adapter.

Loads records for a changeset from Iceberg, applies a dummy transform, and
indexes the documents into Elasticsearch.
"""

import argparse
import json
from typing import Any, Literal

from pydantic import BaseModel, Field
from utils.elasticsearch import get_client, get_standard_index_name

from adapters.axiell import config as axiell_config
from adapters.axiell import helpers as axiell_helpers
from adapters.ebsco import config as ebsco_config
from adapters.ebsco import helpers as ebsco_helpers
from adapters.transformers.ebsco_transformer import EbscoTransformer
from adapters.transformers.manifests import TransformerManifest
from adapters.utils.adapter_store import AdapterStore


class TransformerEvent(BaseModel):
    transformer_type: Literal["axiell", "ebsco"]
    job_id: str | None = None
    changeset_ids: list[str] = Field(default_factory=list)


class TransformResult(BaseModel):
    changeset_ids: list[str] = Field(default_factory=list)
    indexed_count: int
    errors: list[str]
    job_id: str | None = None


def handler(
    event: TransformerEvent,
    es_mode: str = "private",
    use_rest_api_table: bool = False,
) -> TransformerManifest:
    print(f"Processing event: {event}")
    print(f"Received job_id: {event.job_id}")

    if event.transformer_type == "axiell":
        config = axiell_config
        helpers = axiell_helpers
        transformer_class = EbscoTransformer
    elif event.transformer_type == "ebsco":
        config = ebsco_config
        helpers = ebsco_helpers
        transformer_class = EbscoTransformer
    else:
        raise ValueError(f"Unknown transformer type: {event.transformer_type}")

    table = helpers.build_adapter_table(use_rest_api_table)
    table_client = AdapterStore(table)
    transformer = transformer_class(table_client, event.changeset_ids)

    # Determine index date from config override or fallback to pipeline date
    index_date = config.INDEX_DATE or config.PIPELINE_DATE
    index_name = get_standard_index_name(config.ES_INDEX_NAME, index_date)
    print(
        f"Writing to Elasticsearch index: {index_name} in pipeline {config.PIPELINE_DATE} ..."
    )
    
    es_client = get_client(
        pipeline_date=config.PIPELINE_DATE,
        es_mode=es_mode,
        api_key_name=config.ES_API_KEY_NAME,
    )
    transformer.stream_to_index(es_client, index_name)

    # writer = ManifestWriter(
    #     job_id=event.job_id,
    #     changeset_id=changeset_id,
    #     bucket=config.S3_BUCKET,
    #     prefix=config.BATCH_S3_PREFIX,
    # )
    # result = writer.build_manifest(
    #     job_id=event.job_id,
    #     batches_ids=batches_ids,
    #     errors=error_lines,
    #     success_count=total_success,
    # )
    # total_batches = len(batches_ids)
    # total_ids = sum(len(b) for b in batches_ids)
    # 
    # return result


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    request = TransformerEvent.model_validate(event)
    response = handler(request)
    return response.model_dump(mode="json")


def main() -> None:
    parser = argparse.ArgumentParser(description="Transform adapter data")
    parser.add_argument(
        "--transformer-type",
        required=True,
        help="Which transformer to run.",
        choices=["axiell", "ebsco"],
    )
    parser.add_argument(
        "--changeset-id",
        dest="changeset_ids",
        action="append",
        default=[],
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
        type=str,
        help="Where to index source work documents. Use 'public' to connect to the production cluster.",
        choices=["local", "public"],
        default="local",
    )

    args = parser.parse_args()
    event = TransformerEvent(
        transformer_type=args.transformer_type,
        changeset_ids=args.changeset_ids,
        job_id=args.job_id or args.changeset_ids[0],
    )
    response = handler(
        event, use_rest_api_table=args.use_rest_api_table, es_mode=args.es_mode
    )
    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
