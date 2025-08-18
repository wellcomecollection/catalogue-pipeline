from config import (
    LOC_NAMES_URL,
    LOC_SUBJECT_HEADINGS_URL,
    MESH_URL,
)
from models.events import EntityType, IncrementalWindow, TransformerType

from .base_transformer import BaseTransformer
from .catalogue.concepts_transformer import CatalogueConceptsTransformer
from .catalogue.work_identifiers_transformer import CatalogueWorkIdentifiersTransformer
from .catalogue.works_transformer import CatalogueWorksTransformer
from .loc.concepts_transformer import LibraryOfCongressConceptsTransformer
from .loc.locations_transformer import LibraryOfCongressLocationsTransformer
from .loc.names_transformer import LibraryOfCongressNamesTransformer
from .mesh.concepts_transformer import MeSHConceptsTransformer
from .mesh.locations_transformer import MeSHLocationsTransformer
from .wikidata.concepts_transformer import WikidataConceptsTransformer
from .wikidata.locations_transformer import WikidataLocationsTransformer
from .wikidata.names_transformer import WikidataNamesTransformer

CATALOGUE_TRANSFORMERS = {
    "catalogue_concepts",
    "catalogue_works",
    "catalogue_work_identifiers",
}


def create_transformer(
    transformer_type: TransformerType,
    entity_type: EntityType,
    pipeline_date: str | None,
    window: IncrementalWindow | None,
    is_local: bool,
) -> BaseTransformer:
    if window is not None and transformer_type not in CATALOGUE_TRANSFORMERS:
        raise ValueError(
            f"The {transformer_type} transformer does not support incremental mode."
        )

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
        return WikidataConceptsTransformer(entity_type, "loc")
    if transformer_type == "wikidata_linked_loc_locations":
        return WikidataLocationsTransformer(entity_type, "loc")
    if transformer_type == "wikidata_linked_loc_names":
        return WikidataNamesTransformer(entity_type, "loc")
    if transformer_type == "wikidata_linked_mesh_concepts":
        return WikidataConceptsTransformer(entity_type, "mesh")
    if transformer_type == "wikidata_linked_mesh_locations":
        return WikidataLocationsTransformer(entity_type, "mesh")
    if transformer_type == "catalogue_concepts":
        return CatalogueConceptsTransformer(pipeline_date, window, is_local)
    if transformer_type == "catalogue_works":
        return CatalogueWorksTransformer(pipeline_date, window, is_local)
    if transformer_type == "catalogue_work_identifiers":
        return CatalogueWorkIdentifiersTransformer(pipeline_date, window, is_local)

    raise ValueError(f"Unknown transformer type: {transformer_type}")
