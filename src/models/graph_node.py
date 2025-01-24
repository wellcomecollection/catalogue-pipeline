import datetime
from typing import Literal, Optional

from pydantic import BaseModel


# Each node must have a label and an id
class BaseNode(BaseModel):
    id: str
    label: str


# Represents a LoC, MeSH, or Wikidata concept.
# The `id` field stores a unique identifier provided by the source vocabulary/ontology
class SourceConcept(BaseNode):
    source: Literal["nlm-mesh", "lc-subjects", "lc-names", "wikidata"]
    # For example MeSH tree numbers or other identifiers from Wikidata
    alternative_ids: list[str] = []
    # LoC variants, MeSH concepts other than preferred term
    alternative_labels: list[str] = []
    # Concept description, such as MeSH scope note or Wikidata description
    description: Optional[str] = None


# Represents a LoC or Wikidata location. Inherits all fields from SourceConcept, plus optional coordinates.
class SourceLocation(SourceConcept):
    latitude: Optional[float] = None  # Coordinates from Wikidata
    longitude: Optional[float] = None  # Coordinates from Wikidata


# Represents a LoC or Wikidata name. Inherits all fields from SourceConcept, plus other optional fields.
class SourceName(SourceConcept):
    date_of_birth: Optional[datetime.date] = None
    date_of_death: Optional[datetime.date] = None
    place_of_birth: Optional[str] = None

ConceptType = Literal[
        "Person",
        "Concept",
        "Organisation",
        "Place",
        "Agent",
        "Meeting",
        "Genre",
        "Period",
    ]

ConceptSource = Literal[
        "label-derived", "nlm-mesh", "lc-subjects", "lc-names", "viaf", "fihrist"
    ]

# The `id` field stores a canonical Wellcome identifier
class Concept(BaseNode):
    type: ConceptType
    source: ConceptSource