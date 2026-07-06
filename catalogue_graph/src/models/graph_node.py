from typing import Annotated

from pydantic import BaseModel, StringConstraints

from utils.types import ConceptSource, DisplayWorkType

# Matches a Wikidata date, such as 1976-01-01T00:00:00Z or -0005-12-12T00:00:00Z
WIKIDATA_DATE_PATTERN = r"-?\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\dZ"
FormattedDateString = Annotated[str, StringConstraints(pattern=WIKIDATA_DATE_PATTERN)]


# Each node must have a label and an id
class BaseNode(BaseModel):
    id: str
    label: str | None = None

    @classmethod
    def bulk_load_label(cls) -> str:
        """The Neptune node label (`:LABEL`) under which instances are bulk loaded."""
        return cls.__name__


# Represents a LoC, MeSH, or Wikidata concept.
# The `id` field stores a unique identifier provided by the source vocabulary/ontology
class SourceConcept(BaseNode):
    source: ConceptSource
    # For example MeSH tree numbers or other identifiers from Wikidata
    alternative_ids: list[str] = []
    # LoC variants, MeSH concepts other than preferred term
    alternative_labels: list[str] = []
    # Concept description, such as MeSH scope note or Wikidata description
    description: str | None = None
    # URLs of images associated with this concept from the source
    image_urls: list[str] = []


# Represents a LoC or Wikidata location. Inherits all fields from SourceConcept, plus optional coordinates.
class SourceLocation(SourceConcept):
    latitude: float | None = None  # Coordinates from Wikidata
    longitude: float | None = None  # Coordinates from Wikidata


# Represents a LoC or Wikidata name. Inherits all fields from SourceConcept, plus other optional fields.
class SourceName(SourceConcept):
    date_of_birth: FormattedDateString | None = None
    date_of_death: FormattedDateString | None = None
    place_of_birth: str | None = None


# A minimal stand-in for a source concept which is referenced by a catalogue concept but might not have been
# bulk loaded into the graph yet (source ontology loads run monthly; catalogue loads run incrementally).
# The full monthly load enriches the stub in place. The field set must stay identical to `Concept` so that
# both can share a single bulk load CSV (whose header is derived from one model).
class SourceConceptStub(BaseNode):
    source: ConceptSource

    @classmethod
    def bulk_load_label(cls) -> str:
        return "SourceConcept"


class SourceLocationStub(SourceConceptStub):
    @classmethod
    def bulk_load_label(cls) -> str:
        return "SourceLocation"


class SourceNameStub(SourceConceptStub):
    @classmethod
    def bulk_load_label(cls) -> str:
        return "SourceName"


# The `id` field stores a canonical Wellcome identifier
class Concept(BaseNode):
    source: ConceptSource


class Work(BaseNode):
    type: DisplayWorkType
    alternative_labels: list[str] = []
    reference_number: str | None = None
    collection_path: str | None = None
    collection_path_label: str | None = None


class Image(BaseNode):
    location_type: str
    location_url: str


class PathIdentifier(BaseNode):
    pass
