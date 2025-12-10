"""Transformer step for Axiell and EBSCO.

Loads records for a changeset from Iceberg, applies a transform, and
indexes transformed documents into Elasticsearch.
"""

import argparse
import json
from typing import Any, Literal, Protocol, cast

from pydantic import BaseModel, Field

from adapters.axiell import config as axiell_config
from adapters.axiell import helpers as axiell_helpers
from adapters.ebsco import config as ebsco_config
from adapters.ebsco import helpers as ebsco_helpers
from adapters.transformers.axiell_transformer import AxiellTransformer
from adapters.transformers.base_transformer import BaseTransformer
from adapters.transformers.ebsco_transformer import EbscoTransformer
from adapters.transformers.manifests import ManifestWriter, TransformerManifest
from adapters.utils.adapter_store import AdapterStore
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name


class TransformerEvent(BaseModel):
    transformer_type: Literal["axiell", "ebsco"]
    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)


class AdapterConfig(Protocol):
    PIPELINE_DATE: str
    INDEX_DATE: str | None
    ES_INDEX_NAME: str
    ES_API_KEY_NAME: str
    S3_BUCKET: str
    BATCH_S3_PREFIX: str


class AdapterHelpers(Protocol):
    def build_adapter_table(self, use_rest_api_table: bool) -> Any:
        """Construct the Iceberg table containing adapter output."""


def handler(
    event: TransformerEvent,
    es_mode: ElasticsearchMode = "private",
    use_rest_api_table: bool = False,
) -> TransformerManifest:
    print(f"Processing event: {event}")
    print(f"Received job_id: {event.job_id}")

    config: AdapterConfig
    helpers: AdapterHelpers
    transformer_class: type[BaseTransformer]

    if event.transformer_type == "axiell":
        config = cast(AdapterConfig, axiell_config)
        helpers = cast(AdapterHelpers, axiell_helpers)
        transformer_class = AxiellTransformer
    elif event.transformer_type == "ebsco":
        config = cast(AdapterConfig, ebsco_config)
        helpers = cast(AdapterHelpers, ebsco_helpers)
        transformer_class = EbscoTransformer
    else:
        raise ValueError(f"Unknown transformer type: {event.transformer_type}")

    table = helpers.build_adapter_table(use_rest_api_table)
    table_client = AdapterStore(table)
    transformer = transformer_class(table_client, event.changeset_ids)

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
    writer = ManifestWriter(
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        bucket=config.S3_BUCKET,
        prefix=config.BATCH_S3_PREFIX,
    )
    result = writer.build_manifest(
        successful_ids=transformer.successful_ids,
        errors=transformer.errors,
    )
    return result


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    return handler(TransformerEvent.model_validate(event)).model_dump(mode="json")


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
        help="Use the S3 Tables catalog instead of local storage",
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
        job_id=args.job_id,
    )
    response = handler(
        event, use_rest_api_table=args.use_rest_api_table, es_mode=args.es_mode
    )
    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
