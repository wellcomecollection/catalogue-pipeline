from typing import Literal

from transformers.loc.common import remove_id_prefix, RawLibraryOfCongressConcept


class RawLibraryOfCongressSKOSConcept(RawLibraryOfCongressConcept):
    def __init__(self, raw_concept: dict):
        super().__init__(raw_concept)
        self._raw_concept_node = self._extract_concept_node()

    def _extract_concept_node(self) -> dict | None:
        graph: list[dict] = self.raw_concept["@graph"]

        # Some LoC concepts (e.g. deprecated concepts) do not store a concept node in their graph.
        # When this happens, return `None` because there is no concept for us to extract.
        concept_node = next(
            (
                node
                for node in graph
                if self.source_id in node.get("@id", "")
                and node["@type"] == "skos:Concept"
            ),
            None,
        )

        return concept_node

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

    @property
    def label(self) -> str:
        assert self._raw_concept_node is not None

        raw_preferred_label = self._raw_concept_node["skos:prefLabel"]
        return self._extract_label(raw_preferred_label)

    @property
    def alternative_labels(self) -> list[str]:
        """Returns a list of alternative labels for the concept."""
        assert self._raw_concept_node is not None

        raw_alternative_labels = self._raw_concept_node.get("skos:altLabel", [])

        # Raw alternative labels are either returned in a list of labels, or as a single label
        # in the same format as `skos:prefLabel`
        if isinstance(raw_alternative_labels, list):
            return [self._extract_label(item) for item in raw_alternative_labels]

        return [self._extract_label(raw_alternative_labels)]

    def linked_concepts_ids(self, sko_link: str) -> list[str]:
        """Returns a list of IDs representing concepts which are linked to the current concept"""
        assert self._raw_concept_node is not None

        linked_concepts = self._raw_concept_node.get(f"skos:{sko_link}", [])

        # Sometimes linked concepts are returned as a list of concepts, and sometimes as just a single JSON
        if isinstance(linked_concepts, dict):
            linked_concepts = [linked_concepts]

        linked_ids = []
        for concept in linked_concepts:
            # Some linked concepts have IDs in the format `_:n<some_hexadecimal_string>`.
            # These IDs do not exist in the LoC source files or the LoC website, so we filter them out.
            if concept["@id"].startswith("_:n"):
                continue

            linked_ids.append(remove_id_prefix(concept["@id"]))

        return linked_ids

    @property
    def broader_concept_ids(self) -> list[str]:
        """Returns a list of IDs representing concepts which are broader than the current concept."""
        sko_link_type = "broader"
        return self.linked_concepts_ids(sko_link_type)

    @property
    def related_concept_ids(self) -> list[str]:
        """Returns a list of IDs representing concepts which are related to the current concept."""
        sko_link_type = "related"
        return self.linked_concepts_ids(sko_link_type)

    @property
    def is_geographic(self) -> bool:
        """Returns True if the node represents a geographic concept, as determined by `skos:notation`."""
        if self._raw_concept_node is None:
            return False

        # Notations are sometimes returned as a single notation (with a `@type` property, and a `@value` property),
        # and sometimes as a list of notations.
        notation = self._raw_concept_node.get("skos:notation", [])
        if isinstance(notation, dict):
            notation = [notation]

        notation_types = {item.get("@type") for item in notation}
        return "http://id.loc.gov/datatypes/codes/gac" in notation_types

    @property
    def source(self) -> Literal["lc-subjects", "lc-names"]:
        if "subjects" in self.raw_concept["@id"]:
            return "lc-subjects"

        if "names" in self.raw_concept["@id"]:
            return "lc-names"

        raise ValueError("Unknown concept type.")
