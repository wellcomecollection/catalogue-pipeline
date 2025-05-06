from typing import TypedDict

from models.graph_node import ConceptType, WorkType
from sources.catalogue.concepts_source import extract_concepts_from_work

from .raw_concept import RawCatalogueConcept


class WorkConcept(TypedDict):
    id: str
    referenced_type: ConceptType


class RawCatalogueWork:
    def __init__(self, raw_work: dict):
        self.raw_work = raw_work

    @property
    def wellcome_id(self) -> str:
        wellcome_id = self.raw_work["id"]
        assert isinstance(wellcome_id, str)
        return wellcome_id

    @property
    def label(self) -> str:
        label = self.raw_work["title"]
        assert isinstance(label, str)
        return label

    @property
    def type(self) -> WorkType:
        concept_type: WorkType = self.raw_work["type"]
        return concept_type

    @property
    def alternative_labels(self) -> list[str]:
        alternative_titles = self.raw_work["alternativeTitles"]
        assert isinstance(alternative_titles, list)
        return alternative_titles

    @property
    def concepts(self) -> list[WorkConcept]:
        processed = set()
        work_concepts: list[WorkConcept] = []
        for concept, _ in extract_concepts_from_work(self.raw_work):
            raw_concept = RawCatalogueConcept(concept)

            if raw_concept.is_concept and raw_concept.wellcome_id not in processed:
                processed.add(raw_concept.wellcome_id)
                work_concepts.append(
                    {"id": raw_concept.wellcome_id, "referenced_type": raw_concept.type}
                )

        return work_concepts
