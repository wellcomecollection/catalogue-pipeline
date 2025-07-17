from collections.abc import Generator

import config
from models.graph_edge import ConceptHasSourceConcept, ConceptHasSourceConceptAttributes
from models.graph_node import Concept
from sources.catalogue.concepts_source import CatalogueConceptsSource
from utils.elasticsearch import get_standard_index_name
from utils.types import WorkConceptKey

from transformers.base_transformer import BaseTransformer

from .id_label_checker import IdLabelChecker
from .raw_concept import RawCatalogueConcept
from .works_transformer import ES_FIELDS, ES_QUERY


class CatalogueConceptsTransformer(BaseTransformer):
    def __init__(self, pipeline_date: str | None, is_local: bool):
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        self.source = CatalogueConceptsSource(
            pipeline_date, is_local, index_name, ES_QUERY, ES_FIELDS
        )

        self.id_label_checker = None
        self.id_lookup: set = set()

    def transform_node(self, raw_data: tuple[dict, WorkConceptKey]) -> Concept | None:
        raw_concept = RawCatalogueConcept(raw_data[0], self.id_label_checker)

        if not raw_concept.is_concept:
            return None

        if raw_concept.wellcome_id in self.id_lookup:
            return None

        self.id_lookup.add(raw_concept.wellcome_id)

        return Concept(
            id=raw_concept.wellcome_id,
            label=raw_concept.label,
            source=raw_concept.source,
        )

    def extract_edges(
        self, raw_data: tuple[dict, WorkConceptKey]
    ) -> Generator[ConceptHasSourceConcept]:
        if self.id_label_checker is None:
            self.id_label_checker = IdLabelChecker(
                node_types=["concepts", "locations", "names"], sources=["loc", "mesh"]
            )

        raw_concept = RawCatalogueConcept(raw_data[0], self.id_label_checker)

        if not raw_concept.is_concept:
            return

        if raw_concept.wellcome_id in self.id_lookup:
            return

        self.id_lookup.add(raw_concept.wellcome_id)

        # Generate edge via label
        if (
            raw_concept.source == "label-derived"
            and (source_id := raw_concept.label_matched_source_concept_id) is not None
        ):
            attributes = ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="label"
            )
            yield ConceptHasSourceConcept(
                from_id=raw_concept.wellcome_id,
                to_id=source_id,
                attributes=attributes,
            )

        # Generate edge via ID
        if raw_concept.has_valid_source_concept:
            attributes = ConceptHasSourceConceptAttributes(
                qualifier=raw_concept.mesh_qualifier, matched_by="identifier"
            )
            yield ConceptHasSourceConcept(
                from_id=raw_concept.wellcome_id,
                to_id=str(raw_concept.source_concept_id),
                attributes=attributes,
            )
