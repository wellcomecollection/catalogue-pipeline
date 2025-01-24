from typing import Literal, get_args

ConceptType = Literal["Concept",
      "Person",
      "Organisation",
      "Meeting",
      "Period",
      "Subject",
      "Place",
      "Agent",
      "Genre"]


class RawCatalogueConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = self._extract_concept_node(raw_concept)

    @staticmethod
    def _extract_concept_node(raw_concept):
        # There should be either one concept inside a list, or one agent
        if len(raw_concept.get("concepts", [])) > 0:
            return raw_concept["concepts"][0]
        else:
            return raw_concept.get("agent")

    @property
    def is_concept(self) -> bool:
        if isinstance(self.raw_concept.get("type"), str):
            if self.raw_concept["type"] in get_args(ConceptType) and self.raw_concept.get("identifiers"):
                return True
        return False

    @property
    def wellcome_id(self) -> str:
        return self.raw_concept["id"]
    
    @property
    def label(self) -> str:
        return self.raw_concept["label"]
    
    @property
    def type(self) -> ConceptType:
        return self.raw_concept["type"]
    
    def _get_identifier(self) -> dict:
        identifier = self.raw_concept.get("identifiers", [])
        # There should be exactly one source identifier for each concept
        assert(len(identifier) == 1)

        return identifier[0]
    
    @property
    def source(self) -> str:

        identifier = self._get_identifier()
        return identifier["identifierType"]["id"]
