from typing import Annotated, Literal, Optional

from pydantic import BaseModel, StringConstraints

# Matches a Wikidata date, such as 1976-01-01T00:00:00Z or -0005-12-12T00:00:00Z
WIKIDATA_DATE_PATTERN = r"-?\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\dZ"
FormattedDateString = Annotated[str, StringConstraints(pattern=WIKIDATA_DATE_PATTERN)]


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
    date_of_birth: Optional[FormattedDateString] = None
    date_of_death: Optional[FormattedDateString] = None
    place_of_birth: Optional[str] = None


# Catalogue concepts have a specific type and source
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