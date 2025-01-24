from typing import cast, get_args

from models.graph_node import ConceptType, ConceptSource


class RawCatalogueConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = self._extract_concept_node(raw_concept)

    @staticmethod
    def _extract_concept_node(raw_concept: dict) -> dict:
        """
        Extracts raw concepts data from one of two formats:
        Either as a dicitonary inside a list under "concepts", or as a dictionary under "agent".
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
        if isinstance(self.raw_concept.get("type"), str):
            if self.raw_concept["type"] in get_args(ConceptType) and self.raw_concept.get("identifiers"):
                return True
    
        return False

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
        concept_type = self.raw_concept.get("type")
        
        if concept_type in get_args(ConceptType):
            return cast(ConceptType, concept_type)
        
        raise ValueError("Concept type not recognised.")
    
    def _get_identifier(self) -> dict:
        """Returns metadata about the source identifier."""
        raw_identifier = self.raw_concept.get("identifiers", [])
        # There should be exactly one source identifier for each concept
        assert len(raw_identifier) == 1
        identifier = raw_identifier[0]
        
        assert isinstance(identifier, dict)
        return identifier
    
    @property
    def source(self) -> ConceptSource:
        """Returns the concept source (one of "lc-names", "label-derived", etc.)."""

        identifier = self._get_identifier()
        
        source = identifier["identifierType"]["id"]
        if source in get_args(ConceptSource):
            return cast(ConceptSource, source)

        raise ValueError("Concept source not recognised.")
