from collections.abc import Generator

from elasticsearch import Elasticsearch

from graph.sources.catalogue.concepts_source import (
    CatalogueConceptsSource,
    ExtractedWorkConcept,
)
from graph.transformers.graph_transformer import GraphBaseTransformer
from models.events import BasePipelineEvent
from models.graph_edge import ConceptHasSourceConcept, ConceptHasSourceConceptAttributes
from models.graph_node import (
    Concept,
    SourceConceptStub,
    SourceLocationStub,
    SourceNameStub,
)
from utils.ontology import get_transformers_from_ontology
from utils.types import TransformerType

from .id_label_checker import IdLabelChecker, concept_source_from_id
from .raw_concept import RawCatalogueConcept

# The node label each source ontology transformer bulk loads its nodes under, so that stubs
# minted here carry the same label as the full node which later enriches them in place.
STUB_CLASSES_BY_TRANSFORMER: dict[TransformerType, type[SourceConceptStub]] = {
    "loc_concepts": SourceConceptStub,
    "loc_names": SourceNameStub,
    "loc_locations": SourceLocationStub,
    "mesh_concepts": SourceConceptStub,
    "mesh_locations": SourceLocationStub,
}


class CatalogueConceptsTransformer(GraphBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
    ):
        self.source = CatalogueConceptsSource(event, es_client=es_client)

        self.id_label_checker: IdLabelChecker | None = None
        self.id_lookup: set = set()
        self.emitted_source_concept_ids: set[str] = set()
        self.event = event

    def _get_id_label_checker(self) -> IdLabelChecker:
        if self.id_label_checker is None:
            transformers = []
            for ontology in ("mesh", "loc"):
                transformers += get_transformers_from_ontology(ontology)

            self.id_label_checker = IdLabelChecker(transformers, self.event)

        return self.id_label_checker

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

    def extract_supplementary_nodes(
        self, raw_data: ExtractedWorkConcept
    ) -> Generator[SourceConceptStub]:
        """
        Emit a stub node for every matched source concept, guaranteeing that the targets of this
        window's `HAS_SOURCE_CONCEPT` edges exist in the graph even if the corresponding source
        ontology node has not been bulk loaded yet. Stubs are upserted, so the full monthly load
        enriches them in place (and re-emitting one over a full node is a no-op).
        """
        id_label_checker = self._get_id_label_checker()
        raw_concept = RawCatalogueConcept(raw_data.concept, id_label_checker)

        for match in raw_concept.matched_source_concepts():
            if match.source_id in self.emitted_source_concept_ids:
                continue

            self.emitted_source_concept_ids.add(match.source_id)

            source = concept_source_from_id(match.source_id)
            transformer = id_label_checker.get_transformer(match.source_id)
            stub_class = SourceConceptStub
            if transformer is not None:
                stub_class = STUB_CLASSES_BY_TRANSFORMER.get(
                    transformer, SourceConceptStub
                )

            yield stub_class(
                id=match.source_id,
                label=id_label_checker.get_label(match.source_id, source),
                source=source,
            )

    def extract_edges(
        self, raw_data: ExtractedWorkConcept
    ) -> Generator[ConceptHasSourceConcept]:
        raw_concept = RawCatalogueConcept(
            raw_data.concept, self._get_id_label_checker()
        )

        if raw_concept.wellcome_id in self.id_lookup:
            return

        self.id_lookup.add(raw_concept.wellcome_id)

        for match in raw_concept.matched_source_concepts():
            attributes = ConceptHasSourceConceptAttributes(
                qualifier=match.qualifier, matched_by=match.matched_by
            )
            yield ConceptHasSourceConcept(
                from_id=raw_concept.wellcome_id,
                to_id=match.source_id,
                attributes=attributes,
            )
