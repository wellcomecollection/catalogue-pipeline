from collections import Counter, defaultdict
from collections.abc import Generator

import structlog
from elasticsearch import Elasticsearch

from graph.sources.catalogue.concepts_source import (
    CatalogueConceptsSource,
    ExtractedWorkConcept,
)
from graph.transformers.graph_transformer import GraphBaseTransformer
from ingestor.transformers.raw_concept import get_most_specific_concept_type
from models.events import BasePipelineEvent
from models.graph_edge import ConceptHasSourceConcept, ConceptHasSourceConceptAttributes
from models.graph_node import Concept
from utils.ontology import get_transformers_from_ontology
from utils.types import ConceptType

from .id_label_checker import IdLabelChecker
from .raw_concept import RawCatalogueConcept

logger = structlog.get_logger(__name__)


class CatalogueConceptsTransformer(GraphBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
    ):
        self.source = CatalogueConceptsSource(event, es_client=es_client)

        self.id_label_checker: IdLabelChecker | None = None
        self.label_derived_types: dict[str, Counter[ConceptType]] | None = None
        self.id_lookup: set = set()
        self.event = event

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

    def _collect_label_derived_types(self) -> dict[str, Counter[ConceptType]]:
        """Extra pass over the source: works stream in no fixed order, so the first-seen type is run-dependent."""
        # In a windowed run the vote covers only the window's works, so a window with an
        # unusual type mix can match a different type from a full run, which votes over
        # every work. The nightly full edge re-extract and stale-edge removal converge the
        # graph on the full vote (wellcomecollection/platform#6739).
        types: dict[str, Counter[ConceptType]] = defaultdict(Counter)
        for extracted in self.source.stream_raw():
            raw_concept = RawCatalogueConcept(extracted.concept)
            if raw_concept.source == "label-derived":
                types[raw_concept.wellcome_id][raw_concept.type] += 1

        logger.info("Collected label-derived concept types", count=len(types))
        return types

    def _get_match_type(self, raw_concept: RawCatalogueConcept) -> ConceptType:
        """The most common type across works, with the most specific type as tie-break."""
        assert self.label_derived_types is not None

        type_counts = self.label_derived_types.get(raw_concept.wellcome_id)
        if not type_counts:
            return raw_concept.type

        top_count = max(type_counts.values())
        return get_most_specific_concept_type(
            [
                concept_type
                for concept_type, count in type_counts.items()
                if count == top_count
            ]
        )

    def extract_edges(
        self, raw_data: ExtractedWorkConcept
    ) -> Generator[ConceptHasSourceConcept]:
        if self.id_label_checker is None:
            transformers = []
            for ontology in ("mesh", "loc", "weco"):
                transformers += get_transformers_from_ontology(ontology)

            self.id_label_checker = IdLabelChecker(transformers, self.event)

        if self.label_derived_types is None:
            self.label_derived_types = self._collect_label_derived_types()

        raw_concept = RawCatalogueConcept(raw_data.concept, self.id_label_checker)

        if raw_concept.wellcome_id in self.id_lookup:
            return

        self.id_lookup.add(raw_concept.wellcome_id)

        # Generate edge via label
        if (
            raw_concept.source == "label-derived"
            and (
                source_id := raw_concept.get_label_matched_source_concept_id(
                    self._get_match_type(raw_concept)
                )
            )
            is not None
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

        # Generate edge to the Wellcome name authority
        if (weco_id := raw_concept.weco_source_concept_id) is not None:
            attributes = ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            )
            yield ConceptHasSourceConcept(
                from_id=raw_concept.wellcome_id,
                to_id=weco_id,
                attributes=attributes,
            )
