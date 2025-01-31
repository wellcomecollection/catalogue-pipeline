from typing import Literal

ID_PREFIXES_TO_REMOVE = (
    "/authorities/subjects/",
    "http://id.loc.gov/authorities/subjects/",
    "/authorities/names/",
    "http://id.loc.gov/authorities/names/",
)


def remove_id_prefix(raw_id: str) -> str:
    for prefix in ID_PREFIXES_TO_REMOVE:
        raw_id = raw_id.removeprefix(prefix)
    return raw_id


class RawLibraryOfCongressConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept
        self._raw_concept_node = self._extract_concept_node()

    def _extract_concept_node(self) -> dict | None:
        pass

    @property
    def source_id(self) -> str:
        return remove_id_prefix(self.raw_concept["@id"])

    @property
    def source(self) -> Literal["lc-subjects", "lc-names"]:
        if "subjects" in self.raw_concept["@id"]:
            return "lc-subjects"

        if "names" in self.raw_concept["@id"]:
            return "lc-names"

        raise ValueError("Unknown concept type.")

    @staticmethod
    def _extract_label(raw_label: str | dict[str, str] | list[str]) -> str:
        # Labels are either stored directly as strings, or as nested JSON objects with a `@value` property.
        if isinstance(raw_label, str):
            return raw_label

        # In cases where an LoC Name has multiple labels written using different writing systems, labels are returned
        # as a list. When this happens, we extract the first item in the list, which always stores the Latin script
        # version of the label as a string.
        if isinstance(raw_label, list):
            assert isinstance(raw_label[0], str)
            return raw_label[0]

        return raw_label["@value"]

    def exclude(self) -> bool:
        """Returns True if the concept should be excluded from the graph."""
        if self._raw_concept_node is None:
            return True

        # Remove concepts whose IDs have the "-781" suffix. They are duplicates of concepts with non-suffixed IDs.
        # The suffix represents the fact that the concept in question is part of the LCSH - Geographic collection.
        if self.source_id.endswith("-781"):
            return True

        return False
