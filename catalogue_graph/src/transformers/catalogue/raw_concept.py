import re
from typing import get_args

from models.graph_node import ConceptSource, ConceptType

from .id_label_checker import IdLabelChecker


class RawCatalogueConcept:
    def __init__(
        self, raw_concept: dict, id_label_checker: IdLabelChecker | None = None
    ):
        self.raw_concept = self._extract_concept_node(raw_concept)
        self.id_label_checker = id_label_checker

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

    @property
    def label_derived_source_concept_ids(self) -> list[str]:
        assert self.id_label_checker is not None
        
        label_derived_ids = self.id_label_checker.inverse.get(self.label.lower(), [])
        assert isinstance(label_derived_ids, list)
        return label_derived_ids

    @property
    def has_valid_source_concept(self) -> bool:
        """Checks if the source concept ID format matches the specified source."""
        if not isinstance(self.source_concept_id, str):
            return False

        assert self.id_label_checker is not None        
        
        # For MeSH, we not only require that the source identifier has a corresponding node in the graph, 
        # but also that the label of the node matches the label of the catalogue concept
        if self.source == "nlm-mesh" and self.source_concept_id.startswith("D"):
            source_labels = self.id_label_checker.get(self.source_concept_id, [])
            normalised_label = self.label.lower()

            return any(
                source_label in normalised_label for source_label in source_labels
            )
        
        # For LoC, we only require that the referenced source identifier exists in the graph.
        if self.source == "lc-subjects" and self.source_concept_id.startswith("sh"):
            return self.source_concept_id in self.id_label_checker

        if (self.source == "lc-names") and self.source_concept_id.startswith("n"):
            return self.source_concept_id in self.id_label_checker

        return False
