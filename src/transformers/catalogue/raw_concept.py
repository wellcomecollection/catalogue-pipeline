from typing import cast, get_args

from models.graph_node import ConceptType, ConceptSource


class RawCatalogueConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = self._extract_concept_node(raw_concept)

    @staticmethod
    def _extract_concept_node(raw_concept: dict) -> dict:
        # There should be either one concept inside a list, or one agent
        if len(raw_concept.get("concepts", [])) > 0:
            raw_concept_node = raw_concept["concepts"][0]
        else:
            raw_concept_node = raw_concept.get("agent")
        assert isinstance(raw_concept_node, dict)
        return raw_concept_node

    @property
    def is_concept(self) -> bool:
        if isinstance(self.raw_concept.get("type"), str):
            if self.raw_concept["type"] in get_args(ConceptType) and self.raw_concept.get("identifiers"):
                return True
        return False

    @property
    def wellcome_id(self) -> str:
        wellcome_id = self.raw_concept.get("id")
        assert isinstance(wellcome_id, str)
        return wellcome_id
    
    @property
    def label(self) -> str:
        label = self.raw_concept.get("label")
        assert isinstance(label, str)
        return label
    
    @property
    def type(self) -> ConceptType:
        concept_type = self.raw_concept.get("type")
        if concept_type in get_args(ConceptType):
            return cast(ConceptType, concept_type)
        raise ValueError("Concept type not recognised.")
    
    def _get_identifier(self) -> dict:
        raw_identifier = self.raw_concept.get("identifiers", [])
        # There should be exactly one source identifier for each concept
        assert len(raw_identifier) == 1
        identifier = raw_identifier[0]
        
        assert isinstance(identifier, dict)
        return identifier
    
    @property
    def source(self) -> ConceptSource:

        identifier = self._get_identifier()
        
        source = identifier["identifierType"]["id"]
        if source in get_args(ConceptSource):
            return cast(ConceptSource, source)
        raise ValueError("Concept source not recognised.")
