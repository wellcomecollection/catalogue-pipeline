from functools import lru_cache

from utils.aws import (
    get_bulk_load_s3_uri,
    get_csv_from_s3,
    get_transformers_from_ontology,
)
from utils.types import (
    OntologyType,
    TransformerType,
)


@lru_cache
def get_extracted_ids(transformer: TransformerType, pipeline_date: str) -> set[str]:
    """Return all ids extracted as part of the specified transformer."""
    print(f"Retrieving ids of type '{transformer}' from S3.", end=" ", flush=True)

    s3_uri = get_bulk_load_s3_uri(transformer, "nodes", pipeline_date)
    ids = {row[":ID"] for row in get_csv_from_s3(s3_uri)}

    print(f"({len(ids)} ids retrieved.)")

    return ids


def is_id_in_ontology(
    item_id: str, item_ontology: OntologyType, pipeline_date: str
) -> bool:
    """Returns 'True' if the given ID exists in the catalogue graph under the specified ontology."""
    transformers = get_transformers_from_ontology(item_ontology)
    return any(item_id in get_extracted_ids(t, pipeline_date) for t in transformers)
