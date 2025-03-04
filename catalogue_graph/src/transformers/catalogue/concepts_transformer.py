from collections.abc import Generator

from models.graph_edge import ConceptHasSourceConcept, ConceptHasSourceConceptAttributes
from models.graph_node import Concept
from sources.catalogue.concepts_source import CatalogueConceptsSource
from transformers.base_transformer import BaseTransformer

from .id_label_checker import IdLabelChecker
from .raw_concept import RawCatalogueConcept


class CatalogueConceptsTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = CatalogueConceptsSource(url)
        self.id_label_checker = IdLabelChecker.from_source(
            node_type=["concepts", "locations", "names"], source=["loc", "mesh"]
        )
        self.id_lookup: set = set()

    def transform_node(self, raw_node: dict) -> Concept | None:
        raw_concept = RawCatalogueConcept(raw_node, self.id_label_checker)

        if not raw_concept.is_concept:
            return None

        if raw_concept.wellcome_id in self.id_lookup:
            return None

        self.id_lookup.add(raw_concept.wellcome_id)

        return Concept(
            id=raw_concept.wellcome_id,
            label=raw_concept.label,
            source=raw_concept.source,
            type=raw_concept.type,
        )

    def extract_edges(self, raw_node: dict) -> Generator[ConceptHasSourceConcept]:
        raw_concept = RawCatalogueConcept(raw_node, self.id_label_checker)

        if not raw_concept.is_concept:
            return

        if raw_concept.wellcome_id in self.id_lookup:
            return

        self.id_lookup.add(raw_concept.wellcome_id)

        if raw_concept.source == "label-derived":
            # Generate edges via label
            for source_concept_id in raw_concept.label_derived_source_concept_ids:
                attributes = ConceptHasSourceConceptAttributes(
                    qualifier=None, matched_by="label"
                )
                yield ConceptHasSourceConcept(
                    from_id=raw_concept.wellcome_id,
                    to_id=source_concept_id,
                    attributes=attributes,
                )

        if raw_concept.has_valid_source_concept:
            # Generate edges via ID
            attributes = ConceptHasSourceConceptAttributes(
                qualifier=raw_concept.mesh_qualifier, matched_by="identifier"
            )
            yield ConceptHasSourceConcept(
                from_id=raw_concept.wellcome_id,
                to_id=str(raw_concept.source_concept_id),
                attributes=attributes,
            )
