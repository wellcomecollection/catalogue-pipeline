from pydantic import BaseModel
from typing import Literal, Optional


class BaseNode(BaseModel):
    id: str


class SourceConcept(BaseNode):
    # Unique identifier provided by the source vocabulary
    id: str
    # Label/preferred term from source vocabulary
    label: str
    source: Literal["nlm-mesh", "lc-subjects", "wikidata"]
    # For example MeSH tree numbers or other identifiers from Wikidata
    alternative_ids: list[str]
    # LoC variants, MeSH concepts other than preferred term
    alternative_labels: list[str]
    # Concept description, such as MeSH scope note or Wikidata description
    description: Optional[str]


class Concept(BaseNode):
    # Unique Wellcome identifier
    id: str
    label: str
    type: Literal[
        "Person",
        "Concept",
        "Organisation",
        "Place",
        "Agent",
        "Meeting",
        "Genre",
        "Period",
    ]
    source: Literal[
        "label-derived", "nlm-mesh", "lc-subjects", "lc-names", "viaf", "fihrist"
    ]
