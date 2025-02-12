from collections.abc import Generator

from models.graph_edge import ConceptHasSourceConcept
from models.graph_node import Concept
from sources.catalogue.concepts_source import CatalogueConceptsSource
from transformers.base_transformer import BaseTransformer

from .id_label_checker import IdLabelChecker
from .raw_concept import RawCatalogueConcept


class CatalogueConceptsTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = CatalogueConceptsSource(url)
        self.id_label_checker = IdLabelChecker.from_source(
            node_type=["concepts", "locations"], source=["loc", "mesh"]
        )

    def transform_node(self, raw_node: dict) -> Concept | None:
        raw_concept = RawCatalogueConcept(raw_node)

        if not raw_concept.is_concept:
            return None

        return Concept(
            id=raw_concept.wellcome_id,
            label=raw_concept.label,
            source=raw_concept.source,
            type=raw_concept.type,
        )

    def extract_edges(self, raw_node: dict) -> Generator[ConceptHasSourceConcept]:
        raw_concept = RawCatalogueConcept(raw_node)

        if not raw_concept.is_concept:
            return

        if (raw_concept.source == "label-derived") and (
            raw_concept.type not in ["Person", "Organisation", "Agent"]
        ):
            # Generate edges via label
            assert hasattr(self.id_label_checker, "inverse")
            for source_concept_id in self.id_label_checker.inverse.get(
                raw_concept.label.lower(), []
            ):
                yield ConceptHasSourceConcept(
                    from_id=raw_concept.wellcome_id,
                    to_id=source_concept_id,
                    attributes={"qualifier": None, "matched_by": "label"},
                )

        if raw_concept.has_valid_source_concept and (
            (raw_concept.source != "nlm-mesh")
            or (
                self.id_label_checker.get(raw_concept.source_concept_id)
                == raw_concept.label.lower()
            )
        ):
            # Generate edges via ID
            yield ConceptHasSourceConcept(
                from_id=raw_concept.wellcome_id,
                to_id=str(raw_concept.source_concept_id),
                attributes={
                    "qualifier": raw_concept.mesh_qualifier,
                    "matched_by": "identifier",
                },
            )
