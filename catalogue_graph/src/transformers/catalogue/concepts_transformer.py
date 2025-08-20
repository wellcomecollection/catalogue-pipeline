from collections.abc import Generator
from typing import get_args

from models.events import IncrementalWindow
from models.graph_edge import ConceptHasSourceConcept, ConceptHasSourceConceptAttributes
from models.graph_node import Concept
from sources.catalogue.concepts_source import CatalogueConceptsSource
from transformers.base_transformer import BaseTransformer
from utils.types import LocTransformerType, MeshTransformerType, WorkConceptKey

from .id_label_checker import IdLabelChecker
from .raw_concept import RawCatalogueConcept
from .works_transformer import ES_FIELDS, ES_QUERY


class CatalogueConceptsTransformer(BaseTransformer):
    def __init__(
        self,
        pipeline_date: str,
        window: IncrementalWindow | None,
        is_local: bool,
    ):
        self.source = CatalogueConceptsSource(
            pipeline_date, ES_QUERY, ES_FIELDS, window, is_local
        )

        self.id_label_checker: IdLabelChecker | None = None
        self.id_lookup: set = set()
        self.pipeline_date = pipeline_date

    def transform_node(self, raw_data: tuple[dict, WorkConceptKey]) -> Concept | None:
        raw_concept = RawCatalogueConcept(raw_data, self.id_label_checker)

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
            transformers = get_args(MeshTransformerType) + get_args(LocTransformerType)
            self.id_label_checker = IdLabelChecker(
                list(transformers), self.pipeline_date
            )

        raw_concept = RawCatalogueConcept(raw_data, self.id_label_checker)

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
