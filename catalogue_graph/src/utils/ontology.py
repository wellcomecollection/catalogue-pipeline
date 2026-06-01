from functools import lru_cache
from typing import get_args

import structlog

from models.events import BulkLoaderEvent
from utils.aws import get_csv_from_s3
from utils.types import (
    CatalogueTransformerType,
    Environment,
    LocTransformerType,
    MeshTransformerType,
    OntologyType,
    TransformerType,
    WikidataLinkedLocTransformerType,
    WikidataLinkedMeshTransformerType,
    WikidataTransformerType,
)

logger = structlog.get_logger(__name__)

TRANSFORMERS_BY_ONTOLOGY: dict[OntologyType, tuple[TransformerType, ...]] = {
    "wikidata": get_args(WikidataTransformerType),
    "loc": get_args(LocTransformerType),
    "mesh": get_args(MeshTransformerType),
    "catalogue": get_args(CatalogueTransformerType),
    "wikidata_linked_loc": get_args(WikidataLinkedLocTransformerType),
    "wikidata_linked_mesh": get_args(WikidataLinkedMeshTransformerType),
}


def get_transformers_from_ontology(ontology: OntologyType) -> list[TransformerType]:
    """Return a list of all transformer types associated with the given ontology."""

    transformers = TRANSFORMERS_BY_ONTOLOGY.get(ontology)
    if transformers is None:
        raise ValueError(f"Unknown ontology {ontology}")

    return list(transformers)


@lru_cache
def get_ids_for_transformer(
    transformer: TransformerType, pipeline_date: str, environment: Environment
) -> set[str]:
    """Return all ids extracted as part of the specified transformer."""
    logger.info("Retrieving ids from S3", transformer=transformer)

    event = BulkLoaderEvent(
        transformer_type=transformer,
        entity_type="nodes",
        pipeline_date=pipeline_date,
        environment=environment,
    )
    ids = {row[":ID"] for row in get_csv_from_s3(event.get_s3_uri())}

    logger.info("Retrieved ids", transformer=transformer, count=len(ids))

    return ids


def is_id_extracted_for_transformer(
    item_id: str,
    transformer: TransformerType,
    pipeline_date: str,
    environment: Environment,
) -> bool:
    """Return 'True' if the given ID was extracted by the specified transformer."""
    return item_id in get_ids_for_transformer(transformer, pipeline_date, environment)


def is_id_extracted_for_ontology(
    item_id: str,
    item_ontology: OntologyType,
    pipeline_date: str,
    environment: Environment,
) -> bool:
    """Return 'True' if the given ID was extracted by any transformer under the specified ontology."""
    transformers = get_transformers_from_ontology(item_ontology)
    return any(
        item_id in get_ids_for_transformer(t, pipeline_date, environment)
        for t in transformers
    )
