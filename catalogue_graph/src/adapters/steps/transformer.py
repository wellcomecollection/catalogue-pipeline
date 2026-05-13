"""Transformer step for Axiell and EBSCO.

Loads records for a changeset from Iceberg, applies a transform, and
indexes transformed documents into Elasticsearch.
"""

import argparse
import json
import typing
from collections.abc import Callable
from pathlib import PurePosixPath
from typing import Any, Literal, Protocol, cast

import structlog
from pydantic import BaseModel, Field

from adapters.extractors.ebsco import config as ebsco_config
from adapters.extractors.ebsco import helpers as ebsco_helpers
from adapters.extractors.oai_pmh.axiell import config as axiell_config
from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.extractors.oai_pmh.folio import config as folio_config
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.transformers.axiell_reconciler import AxiellReconciler
from adapters.transformers.axiell_transformer import AxiellTransformer
from adapters.transformers.ebsco_transformer import EbscoTransformer
from adapters.transformers.folio_transformer import FolioTransformer
from adapters.transformers.manifests import (
    TransformerManifest,
    TransformerManifestWriter,
)
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.reconciler_store import ReconcilerStore
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


TransformerType = Literal["axiell", "ebsco", "folio", "axiell_reconciler"]


class TransformerEvent(BaseModel):
    transformer_type: TransformerType
    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)
    snapshot_id: int | None = None


class AdapterConfig(Protocol):
    PIPELINE_DATE: str
    INDEX_DATE: str | None
    ES_INDEX_NAME: str
    ES_API_KEY_NAME: str
    S3_BUCKET: str
    S3_PREFIX: str


ICEBERG_NAMESPACE_BY_TYPE: dict[TransformerType, str] = {
    "axiell": "axiell",
    "axiell_reconciler": "axiell",
    "ebsco": "ebsco",
    "folio": "folio",
}

CONFIG_BY_TYPE: dict[TransformerType, AdapterConfig] = {
    "axiell": cast(AdapterConfig, axiell_config),
    "axiell_reconciler": cast(AdapterConfig, axiell_config),
    "ebsco": cast(AdapterConfig, ebsco_config),
    "folio": cast(AdapterConfig, folio_config),
}

ADAPTER_TABLE_BUILDER_BY_TYPE: dict[TransformerType, Callable] = {
    "axiell": AXIELL_CONFIG.build_adapter_table,
    "axiell_reconciler": AXIELL_CONFIG.build_adapter_table,
    "ebsco": ebsco_helpers.build_adapter_table,
    "folio": FOLIO_CONFIG.build_adapter_table,
}

BATCHES_S3_PREFIX_BY_TYPE: dict[TransformerType, str] = {
    "axiell": "transformer/batches",
    "axiell_reconciler": "reconciler/batches",
    "ebsco": "transformer/batches",
    "folio": "transformer/batches",
}


def get_adapter_store(
    transformer_type: TransformerType,
    use_rest_api_table: bool = False,
    create_if_not_exists: bool = False,
) -> AdapterStore:
    build_adapter_table = ADAPTER_TABLE_BUILDER_BY_TYPE[transformer_type]
    namespace = ICEBERG_NAMESPACE_BY_TYPE[transformer_type]
    table = build_adapter_table(
        use_rest_api_table=use_rest_api_table,
        create_if_not_exists=create_if_not_exists,
    )
    return AdapterStore(table, namespace=namespace)


def build_transformer(
    event: TransformerEvent,
    use_rest_api_table: bool = False,
    create_if_not_exists: bool = False,
) -> MarcXmlTransformer:
    adapter_store = get_adapter_store(
        event.transformer_type,
        use_rest_api_table=use_rest_api_table,
        create_if_not_exists=create_if_not_exists,
    )

    snapshot_id = event.snapshot_id or adapter_store.current_snapshot_id()

    if event.transformer_type == "axiell":
        return AxiellTransformer(adapter_store, event.changeset_ids, snapshot_id)
    if event.transformer_type == "ebsco":
        return EbscoTransformer(adapter_store, event.changeset_ids, snapshot_id)
    if event.transformer_type == "folio":
        return FolioTransformer(adapter_store, event.changeset_ids, snapshot_id)
    if event.transformer_type == "axiell_reconciler":
        if not event.changeset_ids:
            # The reconciler doesn't work in the context of a full reindex,
            # since it doesn't preserve historic deleted work GUIDs (source IDs).
            raise ValueError(
                "The reconciler only supports incremental mode. At least one changeset_id required."
            )

        table = AXIELL_CONFIG.build_reconciler_table(
            use_rest_api_table=use_rest_api_table,
            create_if_not_exists=create_if_not_exists,
        )
        reconciler_store = ReconcilerStore(table, namespace="axiell")
        return AxiellReconciler(
            adapter_store, event.changeset_ids, reconciler_store, snapshot_id
        )

    raise ValueError(f"Unknown transformer type: {event.transformer_type}")


def handler(
    event: TransformerEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
    use_rest_api_table: bool = False,
    create_if_not_exists: bool = False,
) -> TransformerManifest:
    setup_logging(execution_context)
    logger.info("Processing transformer event", transformer_event=event.model_dump())
    logger.info("Received job_id", job_id=event.job_id)

    config = CONFIG_BY_TYPE[event.transformer_type]
    transformer = build_transformer(
        event,
        use_rest_api_table=use_rest_api_table,
        create_if_not_exists=create_if_not_exists,
    )

    index_date = config.INDEX_DATE or config.PIPELINE_DATE
    index_name = get_standard_index_name(config.ES_INDEX_NAME, index_date)
    logger.info(
        "Writing to Elasticsearch index",
        index_name=index_name,
        pipeline_date=config.PIPELINE_DATE,
    )

    es_client = get_client(
        pipeline_date=config.PIPELINE_DATE,
        es_mode=es_mode,
        api_key_name=config.ES_API_KEY_NAME,
    )

    transformer.stream_to_index(es_client, index_name)

    s3_batches_prefix = BATCHES_S3_PREFIX_BY_TYPE[event.transformer_type]
    writer = TransformerManifestWriter(
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        snapshot_id=transformer.source.snapshot_id,
        bucket=config.S3_BUCKET,
        prefix=str(PurePosixPath(config.S3_PREFIX, s3_batches_prefix)),
    )
    result = writer.build_manifest(
        successful_ids=transformer.successful_ids,
        errors=transformer.errors,
    )
    return result


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="adapter_transformer",
    )
    transformer_event = TransformerEvent.model_validate(event)
    return handler(
        transformer_event, execution_context, use_rest_api_table=True
    ).model_dump(mode="json")


def main() -> None:
    parser = argparse.ArgumentParser(description="Transform adapter data")
    parser.add_argument(
        "--transformer-type",
        required=True,
        help="Which transformer to run.",
        choices=typing.get_args(TransformerType),
    )
    parser.add_argument(
        "--changeset-id",
        dest="changeset_ids",
        action="append",
        default=[],
        help="Changeset identifier to transform (repeatable)",
    )
    parser.add_argument(
        "--snapshot-id",
        dest="snapshot_id",
        type=int,
        default=None,
        help="An optional Iceberg snapshot ID to use when extracting data from the adapter store",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="Job identifier propagated from the trigger. Will default to 'dev'.",
        default="dev",
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
    parser.add_argument(
        "--create-if-not-exists",
        action="store_true",
        help="Create the Iceberg table if it does not already exist",
    )

    args = parser.parse_args()
    event = TransformerEvent(
        transformer_type=args.transformer_type,
        changeset_ids=args.changeset_ids,
        snapshot_id=args.snapshot_id,
        job_id=args.job_id,
    )
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="adapter_transformer",
    )
    response = handler(
        event,
        execution_context,
        use_rest_api_table=args.use_rest_api_table,
        create_if_not_exists=args.create_if_not_exists,
        es_mode=args.es_mode,
    )
    print(json.dumps(response.model_dump(mode="json")))


if __name__ == "__main__":
    main()
