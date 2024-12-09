from pydantic import BaseModel
from typing import Literal, Optional


class SourceConcept(BaseModel):
    source_id: str  # unique identifier provided by the source vocabulary
    label: str  # label/preferred term from source vocabulary
    source: Literal["nlm-mesh", "lc-subjects", "wikidata"]
    alternative_ids: list[str]  # for example MeSH tree numbers or other identifiers from Wikidata
    alternative_labels: list[str]  # LoC variants, MeSH concepts other than preferred term
    description: Optional[str]  # Concept description, such as MeSH scope note or Wikidata description


class Concept(BaseModel):
    wellcome_id: str
    label: str
    type: Literal["Person", "Concept": "Organisation", "Place", "Agent", "Meeting", "Genre", "Period"]
    source: Literal["label-derived", "nlm-mesh", "lc-subjects", "lc-names", "viaf", "fihrist"]
