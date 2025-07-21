from typing import TypedDict

from models.graph_node import ConceptType, WorkType
from sources.catalogue.concepts_source import extract_concepts_from_work
from utils.types import WorkConceptKey

from .raw_concept import RawCatalogueConcept


class WorkConcept(TypedDict):
    id: str
    referenced_in: WorkConceptKey
    referenced_type: ConceptType


class RawCatalogueWork:
    def __init__(self, raw_work: dict):
        self.raw_work = raw_work
        self.work_data: dict = self.raw_work.get("data", {})
        self.work_state: dict = self.raw_work["state"]

    @property
    def wellcome_id(self) -> str:
        wellcome_id: str = self.work_state["canonicalId"]
        return wellcome_id

    @property
    def label(self) -> str:
        label: str = self.work_data.get("title", "")
        return label

    @property
    def type(self) -> WorkType:
        raw_work_type = self.work_data["workType"]
        work_type: WorkType = "Work" if raw_work_type == "Standard" else raw_work_type
        return work_type

    @property
    def alternative_labels(self) -> list[str]:
        alternative_titles: list[str] = self.work_data.get("alternativeTitles", [])
        return alternative_titles

    @property
    def concepts(self) -> list[WorkConcept]:
        processed = set()
        work_concepts: list[WorkConcept] = []
        for concept, referenced_in in extract_concepts_from_work(self.work_data):
            raw_concept = RawCatalogueConcept(concept)

            if raw_concept.is_concept and raw_concept.wellcome_id not in processed:
                processed.add(raw_concept.wellcome_id)
                work_concepts.append(
                    {
                        "id": raw_concept.wellcome_id,
                        "referenced_in": referenced_in,
                        "referenced_type": raw_concept.type,
                    }
                )

        return work_concepts
