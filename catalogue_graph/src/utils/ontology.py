from functools import lru_cache
from typing import get_args

import utils.bulk_load as bulk_load
from utils.aws import get_csv_from_s3
from utils.types import (
    CatalogueTransformerType,
    LocTransformerType,
    MeshTransformerType,
    OntologyType,
    TransformerType,
    WikidataTransformerType,
)


def get_transformers_from_ontology(ontology: OntologyType) -> list[TransformerType]:
    """Return a list of all transformer types associated with the given ontology."""
    if ontology == "wikidata":
        transformers = get_args(WikidataTransformerType)
    elif ontology == "loc":
        transformers = get_args(LocTransformerType)
    elif ontology == "mesh":
        transformers = get_args(MeshTransformerType)
    elif ontology == "catalogue":
        transformers = get_args(CatalogueTransformerType)
    else:
        raise ValueError(f"Unknown ontology {ontology}.")

    return list(transformers)


@lru_cache
def get_extracted_ids(transformer: TransformerType, pipeline_date: str) -> set[str]:
    """Return all ids extracted as part of the specified transformer."""
    print(f"Retrieving ids of type '{transformer}' from S3.", end=" ", flush=True)

    s3_uri = bulk_load.get_s3_uri(transformer, "nodes", pipeline_date)
    ids = {row[":ID"] for row in get_csv_from_s3(s3_uri)}

    print(f"({len(ids)} ids retrieved.)")

    return ids


def is_id_in_ontology(
    item_id: str, item_ontology: OntologyType, pipeline_date: str
) -> bool:
    """Return 'True' if the given ID exists in the catalogue graph under the specified ontology."""
    transformers = get_transformers_from_ontology(item_ontology)
    return any(item_id in get_extracted_ids(t, pipeline_date) for t in transformers)
