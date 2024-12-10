from pydantic import BaseModel


class BaseEdge(BaseModel):
    from_type: str
    to_type: str
    from_id: str
    to_id: str
    relationship: str
    directed: bool


class SourceConceptNarrowerThan(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    from_id: str
    to_id: str
    relationship: str = "NARROWER_THAN"
    directed: bool = True
