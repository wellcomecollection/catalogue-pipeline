from collections.abc import Generator

from elasticsearch import Elasticsearch

from models.events import BasePipelineEvent
from models.graph_edge import ConceptHasSourceConcept, ConceptHasSourceConceptAttributes
from models.graph_node import Concept
from sources.catalogue.concepts_source import (
    CatalogueConceptsSource,
    ExtractedWorkConcept,
)
from transformers.base_transformer import BaseTransformer
from utils.ontology import get_transformers_from_ontology

from .id_label_checker import IdLabelChecker
from .raw_concept import RawCatalogueConcept


class CatalogueConceptsTransformer(BaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
    ):
        self.source = CatalogueConceptsSource(event, es_client=es_client)

        self.id_label_checker: IdLabelChecker | None = None
        self.id_lookup: set = set()
        self.pipeline_date = event.pipeline_date

    def transform_node(self, extracted: ExtractedWorkConcept) -> Concept | None:
        raw_concept = RawCatalogueConcept(extracted.concept, self.id_label_checker)

        if raw_concept.wellcome_id in self.id_lookup:
            return None

        self.id_lookup.add(raw_concept.wellcome_id)

        return Concept(
            id=raw_concept.wellcome_id,
            label=raw_concept.label,
            source=raw_concept.source,
        )

    def extract_edges(
        self, raw_data: ExtractedWorkConcept
    ) -> Generator[ConceptHasSourceConcept]:
        if self.id_label_checker is None:
            transformers = []
            for ontology in ("mesh", "loc"):
                transformers += get_transformers_from_ontology(ontology)

            self.id_label_checker = IdLabelChecker(transformers, self.pipeline_date)

        raw_concept = RawCatalogueConcept(raw_data.concept, self.id_label_checker)

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
