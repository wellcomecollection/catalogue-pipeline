import re
from typing import Literal


def extract_source_id(raw_item: dict) -> str | None:
    """
    Given a raw node, returns its Library of Congress ID. Returns `None` if the raw ID does not
    correspond to a name or a subject heading.
    """

    # Subject heading IDs always start with an 'sh', followed by a sequence of digits.
    subjects_match = re.search(r"authorities/subjects/(sh\d+)$", raw_item["@id"])
    if subjects_match:
        return subjects_match.group(1)

    # Name IDs always start with an 'n', followed by an optional letter, followed by a sequence of digits.
    names_match = re.search(r"authorities/names/(n[a-z]?\d+)$", raw_item["@id"])
    if names_match:
        return names_match.group(1)

    return None


def extract_source_ids(raw_items: list[dict]) -> list[str]:
    """Extracts Library of Congress IDs from raw nodes."""
    source_ids = []
    for raw_item in raw_items:
        if (source_id := extract_source_id(raw_item)) is not None:
            source_ids.append(source_id)

    return source_ids


class RawLibraryOfCongressConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept
        self._raw_concept_node = self._extract_concept_node()

    def _extract_concept_node(self) -> dict | None:
        graph: list[dict] = self.raw_concept.get("@graph", [])

        if extract_source_id(self.raw_concept) is None:
            return None

        for node in graph:
            # madsrdf:Authority corresponds to the "idea or notion"
            # So the node we are after is the one whose id matches, and is an Authority.
            # Ignore DeprecatedAuthority in this context, as they are to be excluded.
            # https://www.loc.gov/standards/mads/rdf/#t21
            if (
                self.source_id in node.get("@id", "")
                and "madsrdf:Authority" in node["@type"]
                and node.get("madsrdf:authoritativeLabel")
            ):
                return node

        return None

    @property
    def source_id(self) -> str:
        source_id = extract_source_id(self.raw_concept)
        assert isinstance(source_id, str)
        return source_id

    @property
    def source(self) -> Literal["lc-subjects", "lc-names"]:
        if "subjects" in self.raw_concept["@id"]:
            return "lc-subjects"

        if "names" in self.raw_concept["@id"]:
            return "lc-names"

        raise ValueError("Unknown concept type.")

    @property
    def alternative_labels(self) -> list[str]:
        """Returns a list of alternative labels for the concept."""
        assert self._raw_concept_node is not None

        identifier_lookup = {
            n["@id"]: self._extract_value(n["madsrdf:variantLabel"])
            for n in self.raw_concept.get("@graph", [])
            if "madsrdf:Variant" in n["@type"]
        }

        has_variant = _as_list(self._raw_concept_node.get("madsrdf:hasVariant", []))
        raw_alternative_identifiers = [entry["@id"] for entry in has_variant]

        alternative_labels = []
        for identifier in raw_alternative_identifiers:
            alternative_labels.append(identifier_lookup[identifier])

        return alternative_labels

    @property
    def label(self) -> str:
        assert self._raw_concept_node is not None
        raw_preferred_label = self._raw_concept_node["madsrdf:authoritativeLabel"]
        return self._extract_label(raw_preferred_label)

    @property
    def is_geographic(self) -> bool:
        """Returns True if the node represents a geographic concept, as determined by @type"""
        assert self._raw_concept_node is not None
        return "madsrdf:Geographic" in self._raw_concept_node.get("@type", [])

    @property
    def broader_concept_ids(self) -> list[str]:
        """Returns a list of IDs representing concepts which are broader than the current concept."""
        assert self._raw_concept_node is not None

        broader_items = _as_list(
            self._raw_concept_node.get("madsrdf:hasBroaderAuthority", [])
        )
        component_items = _as_list(
            self._raw_concept_node.get("madsrdf:componentList", {}).get("@list", [])
        )
        return extract_source_ids(broader_items + component_items)

    @property
    def narrower_concept_ids(self) -> list[str]:
        """Returns a list of IDs representing concepts which are narrower than the current concept."""
        assert self._raw_concept_node is not None

        narrower_terms = _as_list(
            self._raw_concept_node.get("madsrdf:hasNarrowerAuthority", [])
        )
        return extract_source_ids(narrower_terms)

    @property
    def related_concept_ids(self) -> list[str]:
        """Returns a list of IDs representing concepts which are related to the current concept."""
        assert self._raw_concept_node is not None

        raw_related_ids = _as_list(
            self._raw_concept_node.get("madsrdf:hasReciprocalAuthority", [])
        )
        return extract_source_ids(raw_related_ids)

    @staticmethod
    def _extract_value(dict_or_str: str | dict[str, str]) -> str:
        """Returns value of a raw concept field which is either stored as a sting or dictionary "@value"."""
        if isinstance(dict_or_str, str):
            return dict_or_str

        return dict_or_str["@value"]

    def _extract_label(self, raw_label: str | dict[str, str] | list[str]) -> str:
        # Labels are either stored directly as strings, or as nested JSON objects with a `@value` property.
        # In cases where an LoC Name has multiple labels written using different writing systems, labels are returned
        # as a list. When this happens, we extract the first item in the list, which always stores the Latin script
        # version of the label as a string.
        if isinstance(raw_label, list):
            assert isinstance(raw_label[0], str)
            return raw_label[0]

        return self._extract_value(raw_label)

    def exclude(self) -> bool:
        """Returns True if the concept should be excluded from the graph."""
        return (
            extract_source_id(self.raw_concept) is None
            or self._raw_concept_node is None
        )


def _as_list(dict_or_list: dict | list[dict]) -> list[dict]:
    # Some fields in the source data may contain one or more values
    # When it contains multiple values, it will be a list,
    # but in the case where they contain just one value, it is not.
    # Wrap bare single values in a list, for consistency of processing downstream
    if isinstance(dict_or_list, dict):
        return [dict_or_list]
    return dict_or_list
