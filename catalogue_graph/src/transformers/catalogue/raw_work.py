import json
from typing import TypedDict

from models.graph_node import ConceptType, WorkStatus, WorkType
from sources.catalogue.concepts_source import extract_concepts_from_work
from sources.catalogue.work_identifiers_source import extract_identifiers_from_work
from utils.types import WorkConceptKey, WorkIdentifiersKey

from .raw_concept import RawCatalogueConcept
from .raw_work_identifier import RawCatalogueWorkIdentifier


class WorkConcept(TypedDict):
    id: str
    referenced_in: WorkConceptKey
    referenced_type: ConceptType


class WorkIdentifier(TypedDict):
    id: str
    referenced_in: WorkIdentifiersKey


class RawCatalogueWork:
    def __init__(self, raw_work: dict):
        self.raw_work = raw_work
        self.work_data: dict = self.raw_work.get("data", {})
        self.work_state: dict = self.raw_work["state"]

    def _get_stringified_field(self, field_name: str) -> str | None:
        if field_name in self.work_data:
            return json.dumps(self.work_data[field_name])

        return None

    def _get_optional_string(self, key: str) -> str | None:
        value = self.work_data.get(key)
        if value is not None:
            assert isinstance(value, str)

        return value

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
        work_type: WorkType = self.work_data.get("type", "Work")
        return work_type

    @property
    def status(self) -> WorkStatus:
        work_status: WorkStatus = self.raw_work["type"]
        return work_status

    @property
    def availabilities(self) -> list[str]:
        availabilities: list[dict] = self.work_state.get("availabilities", [])
        return [a["id"] for a in availabilities]

    @property
    def lettering(self) -> str | None:
        return self._get_optional_string("lettering")

    @property
    def reference_number(self) -> str | None:
        return self._get_optional_string("referenceNumber")

    @property
    def description(self) -> str | None:
        return self._get_optional_string("description")

    @property
    def physical_description(self) -> str | None:
        return self._get_optional_string("physicalDescription")

    @property
    def format_id(self) -> str | None:
        format_id: str | None = self.work_data.get("format", {}).get("id")
        return format_id

    @property
    def format_label(self) -> str | None:
        format_label: str | None = self.work_data.get("format", {}).get("label")
        return format_label

    @property
    def edition(self) -> str | None:
        return self._get_optional_string("edition")

    @property
    def duration(self) -> int | None:
        duration: int | None = self.work_data.get("duration")
        return duration

    @property
    def current_frequency(self) -> str | None:
        return self._get_optional_string("currentFrequency")

    @property
    def former_frequency(self) -> list[str]:
        former_frequency: list[str] = self.work_data.get("formerFrequency", [])
        return former_frequency

    @property
    def alternative_labels(self) -> list[str]:
        alternative_labels: list[str] = self.work_data.get("alternativeTitles", [])
        return alternative_labels

    @property
    def designation(self) -> list[str]:
        designation: list[str] = self.work_data.get("designation", [])
        return designation

    @property
    def collection_path_label(self) -> str | None:
        label: str | None = self.work_data.get("collectionPath", {}).get("label")
        return label

    @property
    def other_identifiers(self) -> str | None:
        return self._get_stringified_field("otherIdentifiers")

    @property
    def created_date(self) -> str | None:
        return self._get_stringified_field("createdDate")

    @property
    def thumbnail(self) -> str | None:
        return self._get_stringified_field("thumbnail")

    @property
    def production(self) -> str | None:
        return self._get_stringified_field("production")

    @property
    def languages(self) -> str | None:
        return self._get_stringified_field("languages")

    @property
    def notes(self) -> str | None:
        return self._get_stringified_field("notes")

    @property
    def items(self) -> str | None:
        return self._get_stringified_field("items")

    @property
    def holdings(self) -> str | None:
        return self._get_stringified_field("holdings")

    @property
    def image_data(self) -> str | None:
        return self._get_stringified_field("imageData")

    @property
    def concepts(self) -> list[WorkConcept]:
        # TODO: Do not extract concepts from removed works
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

    @property
    def identifiers(self) -> list[WorkIdentifier]:
        work_identifiers: list[WorkIdentifier] = []

        for identifier, path, referenced_in in extract_identifiers_from_work(
            self.raw_work
        ):
            raw_identifier = RawCatalogueWorkIdentifier(identifier, path)

            work_identifiers.append(
                {"id": raw_identifier.unique_id, "referenced_in": referenced_in}
            )

        return work_identifiers
