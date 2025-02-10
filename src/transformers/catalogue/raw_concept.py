import re
from typing import get_args

from models.graph_node import ConceptSource, ConceptType


class RawCatalogueConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = self._extract_concept_node(raw_concept)

    @staticmethod
    def _extract_concept_node(raw_concept: dict) -> dict:
        """
        Extracts raw concepts data from one of two formats:
        Either as a dictionary inside a list under "concepts", or as a dictionary under "agent".
        """
        if len(raw_concept.get("concepts", [])) > 0:
            raw_concept_node = raw_concept["concepts"][0]
        else:
            raw_concept_node = raw_concept.get("agent")

        assert isinstance(raw_concept_node, dict)
        return raw_concept_node

    @property
    def is_concept(self) -> bool:
        """
        Determines whether a given block of JSON represents a Concept as returned from the Catalogue API.
        A Concept is a block of JSON with a type property and a list of identifiers.
        """
        return (
            self.raw_concept.get("type") in get_args(ConceptType)
            and self.raw_concept.get("identifiers") is not None
        )

    @property
    def wellcome_id(self) -> str:
        """Returns the canonical Wellcome identifier."""
        wellcome_id = self.raw_concept.get("id")

        assert isinstance(wellcome_id, str)
        return wellcome_id

    @property
    def label(self) -> str:
        """Returns the concept label."""
        label = self.raw_concept.get("label")

        assert isinstance(label, str)
        return label

    @property
    def type(self) -> ConceptType:
        """Returns the concept type (one of "Person", "Concept", "Genre", etc.)."""
        concept_type: ConceptType = self.raw_concept["type"]
        return concept_type

    @property
    def raw_identifier(self) -> dict:
        """Returns metadata about the source identifier."""
        identifier_metadata = self.raw_concept.get("identifiers", [])
        # There should be exactly one source identifier for each concept
        assert len(identifier_metadata) == 1
        raw_identifier = identifier_metadata[0]

        assert isinstance(raw_identifier, dict)
        return raw_identifier

    @property
    def source(self) -> ConceptSource:
        """Returns the concept source (one of "lc-names", "label-derived", etc.)."""
        source: ConceptSource = self.raw_identifier["identifierType"]["id"]
        return source

    @property
    def mesh_qualifier(self) -> str | None:
        """Returns MeSH qualifier ID, if present."""
        if self.source == "nlm-mesh":
            qualifier = re.search(r"Q\d+", self.raw_identifier.get("value", ""))
            if qualifier is not None:
                return qualifier.group()

        return None

    @property
    def source_concept_id(self) -> str | None:
        """Returns ID of source concept, if present."""
        source_id = self.raw_identifier.get("value")
        if isinstance(source_id, str):
            if isinstance(self.mesh_qualifier, str):
                source_id = source_id.replace(self.mesh_qualifier, "")
            return source_id

        return None
