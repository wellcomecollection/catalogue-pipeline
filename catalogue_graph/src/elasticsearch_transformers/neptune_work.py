import json

from models.indexable_work import (
    DisplayConcept,
    DisplayContributor,
    DisplayDigitalLocation,
    DisplayGenre,
    DisplayHoldings,
    DisplayId,
    DisplayIdentifier,
    DisplayIdLabel,
    DisplayItem,
    DisplayNote,
    DisplayProductionEvent,
    DisplayRelation,
    DisplaySubject,
)

from elasticsearch_transformers.display_identifier import get_display_identifier
from elasticsearch_transformers.display_location import get_display_location

from .neptune_concept import get_priority_source_concept_value


class RawNeptuneWork:
    def __init__(self, neptune_work: dict, work_hierarchy: dict, work_concepts: list):
        self.raw_work = neptune_work["work"]["~properties"]
        self.raw_identifiers = neptune_work["identifiers"]
        self.work_hierarchy = work_hierarchy or {}
        self.raw_work_concepts = work_concepts or []

    def _get_optional_string(self, key: str) -> str | None:
        value = self.raw_work.get(key)
        if value is not None:
            assert isinstance(value, str)

        return value

    def _extract_list(self, key: str) -> list[str]:
        values: list[str] = self.raw_work.get(key, "").split("||")
        return [v for v in values if len(v) > 0]

    def _extract_json_list(self, key: str) -> list[dict]:
        if self.raw_work.get(key) is None:
            return []

        return json.loads(self.raw_work[key])

    def _extract_dict(self, key: str) -> dict | None:
        if self.raw_work.get(key) is None:
            return None

        return json.loads(self.raw_work[key])

    def _extract_raw_concepts(self, referenced_in: str) -> list[dict]:
        return [
            c for c in self.raw_work_concepts if c["referenced_in"] == referenced_in
        ]

    @property
    def wellcome_id(self) -> str:
        wellcome_id: str = self.raw_work["id"]
        return wellcome_id

    @property
    def title(self) -> str:
        title: str = self.raw_work["label"]
        return title

    @property
    def identifiers(self) -> list[DisplayIdentifier]:
        identifiers = []
        for identifier in self.raw_identifiers:
            source_id = identifier["~properties"]
            value = source_id["id"].split("||")[1]
            display_id = get_display_identifier(value, source_id["label"])
            identifiers.append(display_id)

        return identifiers

    @property
    def reference_number(self) -> str | None:
        return self._get_optional_string("reference_number")

    @property
    def physical_description(self) -> str | None:
        return self._get_optional_string("physical_description")

    @property
    def lettering(self) -> str | None:
        return self._get_optional_string("lettering")

    @property
    def edition(self) -> str | None:
        return self._get_optional_string("edition")

    @property
    def duration(self) -> int | None:
        duration: int | None = self.raw_work.get("duration")
        return duration

    @property
    def alternative_titles(self) -> list[str]:
        return self._extract_list("alternative_labels")

    @property
    def description(self) -> str | None:
        return self._get_optional_string("description")

    @property
    def current_frequency(self) -> str | None:
        return self._get_optional_string("current_frequency")

    @property
    def former_frequency(self) -> list[str]:
        return self._extract_list("former_frequency")

    @property
    def designation(self) -> list[str]:
        return self._extract_list("designation")

    @property
    def thumbnail(self) -> DisplayDigitalLocation | None:
        thumbnail = self._extract_dict("thumbnail")
        if thumbnail is None:
            return

        return get_display_location(thumbnail)

    @property
    def work_type(self) -> DisplayIdLabel | None:
        if "format_id" not in self.raw_work:
            return None

        return DisplayIdLabel(
            id=self.raw_work["format_id"],
            label=self.raw_work["format_label"],
            type="Format",
        )

    @property
    def languages(self) -> list[DisplayIdLabel]:
        languages = []
        for language in self._extract_json_list("languages"):
            languages.append(DisplayIdLabel(**language, type="Language"))

        return languages

    @property
    def notes(self) -> list[DisplayNote]:
        notes = []

        # TODO: Group by note type
        for note in self._extract_json_list("notes"):
            notes.append(
                DisplayNote(
                    contents=[note["contents"]],
                    noteType=DisplayIdLabel(**note["noteType"], type="NoteType"),
                )
            )

        return notes

    @property
    def created_date(self) -> DisplayConcept | None:
        created_date = self._extract_dict("created_date")
        if created_date is None:
            return None

        return DisplayConcept(label=created_date["label"], type="Period")

    @property
    def items(self) -> list[DisplayItem]:
        items = []

        for item in self._extract_json_list("items"):
            identifiers = []

            raw_identifiers = item["id"].get("otherIdentifiers", [])
            if "sourceIdentifier" in item["id"]:
                raw_identifiers.append(item["id"]["sourceIdentifier"])

            for raw_id in raw_identifiers:
                display_id = get_display_identifier(
                    raw_id["value"], raw_id["identifierType"]["id"]
                )
                identifiers.append(display_id)

            locations = [get_display_location(loc) for loc in item.get("locations")]

            items.append(
                DisplayItem(
                    id=item["id"].get("canonicalId"),
                    identifiers=identifiers,
                    title=item.get("title"),
                    note=item.get("note"),
                    locations=locations,
                )
            )

        return items

    @property
    def holdings(self):
        holdings = []

        for holding in self._extract_json_list("holdings"):
            location = None
            if "location" in holding:
                location = get_display_location(holding["location"])

            holdings.append(
                DisplayHoldings(
                    note=holding.get("note"),
                    enumeration=holding.get("enumeration", []),
                    location=location,
                )
            )

        return holdings

    @property
    def images(self):
        images = []
        for image in self._extract_json_list("image_data"):
            images.append(DisplayId(id=image["id"]["canonicalId"], type="Image"))

        return images

    @property
    def production(self):
        events = []
        for event in self._extract_json_list("production"):
            events.append(
                DisplayProductionEvent(
                    label=event["label"],
                    places=[
                        DisplayConcept(label=p["label"], type="Place")
                        for p in event["places"]
                    ],
                    agents=[
                        DisplayConcept(label=p["label"], type="Agent")
                        for p in event["agents"]
                    ],
                    dates=[
                        DisplayConcept(label=p["label"], type="Period")
                        for p in event["dates"]
                    ],
                    function=(
                        DisplayConcept(label=event["function"]["label"])
                        if event.get("function")
                        else None
                    ),
                )
            )

        return events

    def _get_display_relation(self, raw_related: dict, total_parts: int):
        return DisplayRelation(
            id=raw_related["id"],
            title=raw_related["title"],
            referenceNumber=raw_related["reference"],
            totalParts=total_parts,
        )

    @property
    def part_of(self) -> list[DisplayRelation]:
        if "parent" not in self.work_hierarchy:
            return []

        sibling_count = len(self.work_hierarchy.get("siblings", [])) + 1
        parent = self._get_display_relation(
            self.work_hierarchy["parent"], sibling_count
        )

        # TODO: Handle series
        return [parent]

    # @property
    # def preceded_by(self) -> list[DisplayRelation]:
    #     preceded_by = []
    #     for sibling in self.work_hierarchy.get("siblings", []):
    #         if (sibling["reference"] or sibling["title"]) < (
    #             self.reference_number or self.title
    #         ):
    #             preceded_by.append(self._get_display_relation(sibling, 0))
    #
    #     return sorted(preceded_by, key=lambda item: item.referenceNumber or item.title)
    #
    # @property
    # def succeeded_by(self) -> list[DisplayRelation]:
    #     succeeded_by = []
    #     for sibling in self.work_hierarchy.get("siblings", []):
    #         if (sibling["reference"] or sibling["title"]) > (
    #             self.reference_number or self.title
    #         ):
    #             succeeded_by.append(self._get_display_relation(sibling, 0))
    #
    #     return sorted(succeeded_by, key=lambda item: item.referenceNumber or item.title)

    @property
    def parts(self) -> list[DisplayRelation]:
        parts = []
        for child in self.work_hierarchy.get("children", []):
            if child["id"] is not None:
                parts.append(self._get_display_relation(child, 0))

        return sorted(parts, key=lambda item: item.referenceNumber or item.title)

    def _get_display_concept(self, raw_concept: dict) -> DisplayConcept:
        source_concepts = raw_concept["other_source_concepts"]
        if raw_concept["linked_source_concept"] is not None:
            source_concepts.append(raw_concept["linked_source_concept"])

        identifiers = []
        for source_concept in source_concepts:
            identifiers.append(
                get_display_identifier(
                    source_concept["~properties"]["id"],
                    source_concept["~properties"]["source"],
                )
            )

        label, _ = get_priority_source_concept_value(
            raw_concept["concept"], source_concepts, "label"
        )

        # if raw_concept["referenced_type"] is None:
        #     print(raw_concept)

        return DisplayConcept(
            id=raw_concept["concept"]["~properties"]["id"],
            label=label,
            identifiers=identifiers,
            type=raw_concept["referenced_type"] or "Concept",
        )

    @property
    def contributors(self) -> list[DisplayContributor]:
        contributors = []

        for raw_contributor in self._extract_raw_concepts("contributors"):
            contributors.append(
                DisplayContributor(
                    agent=self._get_display_concept(raw_contributor),
                    roles=[],  # TODO: Is this ever populated?
                    primary=False,  # TODO: Extract this info
                )
            )

        return contributors

    @property
    def genres(self) -> list[DisplayGenre]:
        genres = []

        for raw_genre in self._extract_raw_concepts("genres"):
            concept = self._get_display_concept(raw_genre)

            # TODO: Are there genres with multiple concepts?
            genres.append(DisplayGenre(concepts=[concept], label=concept.label))

        return genres

    @property
    def subjects(self) -> list[DisplaySubject]:
        subjects = []

        for raw_subject in self._extract_raw_concepts("subjects"):
            concept = self._get_display_concept(raw_subject)
            # TODO: Handle subjects with multiple concepts
            subjects.append(
                DisplaySubject(label=concept.label, id=concept.id, concepts=[concept])
            )

        return subjects

    # TODO: Recursive partOf
    # TODO: availabilities
    # TODO: collection_path_label
