from typing import TypedDict

from sources.catalogue.concepts_source import extract_concepts_from_work
from utils.types import ConceptType, DisplayWorkType, WorkConceptKey

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
    def type(self) -> DisplayWorkType:
        raw_work_type = self.work_data["workType"]

        # Replace the type 'Standard' (used in the denormalised index) with type 'Work' (used in the final index).
        work_type: DisplayWorkType = (
            "Work" if raw_work_type == "Standard" else raw_work_type
        )
        return work_type

    @property
    def alternative_labels(self) -> list[str]:
        alternative_titles: list[str] = self.work_data.get("alternativeTitles", [])
        return alternative_titles

    @property
    def reference_number(self) -> str | None:
        reference_number: str | None = self.work_data.get("referenceNumber")
        return reference_number

    @property
    def concepts(self) -> list[WorkConcept]:
        processed = set()
        work_concepts: list[WorkConcept] = []
        for raw_data in extract_concepts_from_work(self.work_data):
            raw_concept = RawCatalogueConcept(raw_data)

            if raw_concept.is_concept and raw_concept.wellcome_id not in processed:
                processed.add(raw_concept.wellcome_id)
                work_concepts.append(
                    {
                        "id": raw_concept.wellcome_id,
                        "referenced_in": raw_concept.referenced_in,
                        "referenced_type": raw_concept.type,
                    }
                )

        return work_concepts

    @property
    def identifiers(self) -> list[str]:
        source_identifier = self.work_state["sourceIdentifier"]
        other_identifiers = self.work_data.get("otherIdentifiers", [])

        all_identifiers = [source_identifier] + other_identifiers
        return [i["value"] for i in all_identifiers]

    @property
    def raw_path(self) -> str | None:
        path: str | None = self.work_data.get("collectionPath", {}).get("path")

        if path is None or len(path) == 0:
            return None

        return path

    @property
    def path(self) -> str | None:
        if self.raw_path is None:
            return None

        # A small number of works have a trailing slash in their collection path which must be removed
        # to correctly extract parent path identifiers
        return self.raw_path.rstrip("/")

    @property
    def path_label(self) -> str | None:
        label: str | None = self.work_data.get("collectionPath", {}).get("label")

        if label is None or len(label) == 0:
            return None

        return label

    @property
    def path_identifier(self) -> str | None:
        if self.path is None:
            return None

        # All works which are part of a hierarchy have a corresponding 'path identifier' node representing
        # its position in the hierarchy. In most (but not all) cases, the path identifier equals the work's
        # source identifier or one of its other identifiers.

        # Path identifiers are extracted from the work's collection path. In most cases, the collection path exactly
        # matches the work's Calm ref identifier. In such cases, the path identifier should also match the collection
        # path.
        for identifier in self.identifiers:
            if identifier == self.raw_path:
                return self.path

        # In all other cases, the collection path consists of slash-separated 'fragments', each of which represents
        # a path identifier. The last fragment represents the path identifier of the current work, with other
        # fragments representing its ancestors (e.g. grandparentId/parentId/childId).
        path_fragments = self.path.split("/")
        return path_fragments[-1]

    @property
    def parent_path_identifier(self) -> str | None:
        if self.path is None or "/" not in self.path:
            return None

        path_fragments = self.path.split("/")
        for identifier in self.identifiers:
            if identifier == self.raw_path:
                return "/".join(path_fragments[:-1])

        return path_fragments[-2]
