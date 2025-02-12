from pydantic import BaseModel


class BaseEdge(BaseModel):
    from_type: str
    to_type: str
    from_id: str
    to_id: str
    relationship: str
    directed: bool
    attributes: dict = {}


class SourceConceptNarrowerThan(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "NARROWER_THAN"
    directed: bool = True


class SourceConceptSameAs(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "SAME_AS"
    directed: bool = False


class SourceConceptRelatedTo(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "RELATED_TO"
    directed: bool = False


class SourceConceptHasParent(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "HAS_PARENT"
    directed: bool = True


class SourceConceptHasFieldOfWork(BaseEdge):
    from_type: str = "SourceName"
    to_type: str = "SourceConcept"
    relationship: str = "HAS_FIELD_OF_WORK"
    directed: bool = True
