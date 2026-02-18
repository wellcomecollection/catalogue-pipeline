from typing import get_args

from elasticsearch import Elasticsearch

from config import (
    LOC_NAMES_URL,
    LOC_SUBJECT_HEADINGS_URL,
    MESH_URL,
)
from models.events import (
    ExtractorEvent,
)
from utils.types import CatalogueTransformerType

from .base_transformer import BaseTransformer
from .catalogue.concepts_transformer import CatalogueConceptsTransformer
from .catalogue.work_identifiers_transformer import CatalogueWorkIdentifiersTransformer
from .catalogue.works_transformer import CatalogueWorksTransformer
from .loc.concepts_transformer import LibraryOfCongressConceptsTransformer
from .loc.locations_transformer import LibraryOfCongressLocationsTransformer
from .loc.names_transformer import LibraryOfCongressNamesTransformer
from .mesh.concepts_transformer import MeSHConceptsTransformer
from .mesh.locations_transformer import MeSHLocationsTransformer
from .weco_concepts.concepts_transformer import WeCoConceptsTransformer
from .wikidata.concepts_transformer import WikidataConceptsTransformer
from .wikidata.locations_transformer import WikidataLocationsTransformer
from .wikidata.names_transformer import WikidataNamesTransformer


def create_transformer(
    event: ExtractorEvent,
    es_client: Elasticsearch | None,
) -> BaseTransformer:
    transformer_type = event.transformer_type
    entity_type = event.entity_type
    pipeline_date = event.pipeline_date

    if event.window is not None and transformer_type not in get_args(
        CatalogueTransformerType
    ):
        raise ValueError(
            f"The {transformer_type} transformer does not support incremental mode. "
            "Only catalogue transformers support incremental (window-based) processing."
        )
    if transformer_type == "weco_concepts":
        return WeCoConceptsTransformer()
    if transformer_type == "loc_concepts":
        return LibraryOfCongressConceptsTransformer(LOC_SUBJECT_HEADINGS_URL)
    if transformer_type == "loc_names":
        return LibraryOfCongressNamesTransformer(LOC_NAMES_URL)
    if transformer_type == "loc_locations":
        return LibraryOfCongressLocationsTransformer(
            LOC_SUBJECT_HEADINGS_URL, LOC_NAMES_URL
        )
    if transformer_type == "mesh_concepts":
        return MeSHConceptsTransformer(MESH_URL)
    if transformer_type == "mesh_locations":
        return MeSHLocationsTransformer(MESH_URL)
    if transformer_type == "wikidata_linked_loc_concepts":
        return WikidataConceptsTransformer("loc_concepts", entity_type, pipeline_date)
    if transformer_type == "wikidata_linked_loc_locations":
        return WikidataLocationsTransformer("loc_locations", entity_type, pipeline_date)
    if transformer_type == "wikidata_linked_loc_names":
        return WikidataNamesTransformer("loc_names", entity_type, pipeline_date)
    if transformer_type == "wikidata_linked_mesh_concepts":
        return WikidataConceptsTransformer("mesh_concepts", entity_type, pipeline_date)
    if transformer_type == "wikidata_linked_mesh_locations":
        return WikidataLocationsTransformer(
            "mesh_locations", entity_type, pipeline_date
        )
    if transformer_type == "catalogue_concepts":
        if es_client is None:
            raise ValueError(
                "Elasticsearch client is required for catalogue transformers."
            )
        return CatalogueConceptsTransformer(event, es_client)
    if transformer_type == "catalogue_works":
        if es_client is None:
            raise ValueError(
                "Elasticsearch client is required for catalogue transformers."
            )
        return CatalogueWorksTransformer(event, es_client)
    if transformer_type == "catalogue_work_identifiers":
        if es_client is None:
            raise ValueError(
                "Elasticsearch client is required for catalogue transformers."
            )
        return CatalogueWorkIdentifiersTransformer(event, es_client)

    raise ValueError(f"Unknown transformer type: {transformer_type}")
