from pydantic import BaseModel


class BaseEdge(BaseModel):
    from_type: str
    to_type: str
    from_id: str
    to_id: str
    relationship: str
    attributes: dict = {}


class SourceConceptNarrowerThan(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "NARROWER_THAN"


class SourceConceptSameAs(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "SAME_AS"
