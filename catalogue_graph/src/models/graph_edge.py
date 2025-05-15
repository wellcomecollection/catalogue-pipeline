from pydantic import BaseModel

from models.graph_node import ConceptType
from utils.types import WorkConceptKey


class EdgeAttributes(BaseModel):
    pass


class SourceConceptSameAsAttributes(EdgeAttributes):
    source: str


class SourceNameRelatedToAttributes(EdgeAttributes):
    relationship_type: str


class ConceptHasSourceConceptAttributes(EdgeAttributes):
    qualifier: str | None
    matched_by: str


class WorkHasConceptAttributes(EdgeAttributes):
    referenced_in: WorkConceptKey
    referenced_type: ConceptType


def get_all_edge_attributes() -> set[str]:
    """Returns a set of all possible edge attributes from all edge types."""
    attribute_classes: list[type[EdgeAttributes]] = [
        SourceNameRelatedToAttributes,
        SourceConceptSameAsAttributes,
        ConceptHasSourceConceptAttributes,
        WorkHasConceptAttributes,
    ]

    attributes = set()
    for attribute_class in attribute_classes:
        for annotation in attribute_class.__annotations__:
            attributes.add(annotation)
    return attributes


class BaseEdge(BaseModel):
    from_type: str
    to_type: str
    from_id: str
    to_id: str
    relationship: str
    directed: bool
    attributes: EdgeAttributes = EdgeAttributes()


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


class SourceNameRelatedTo(BaseEdge):
    from_type: str = "SourceName"
    to_type: str = "SourceName"
    relationship: str = "RELATED_TO"
    directed: bool = True


class SourceConceptHasParent(BaseEdge):
    from_type: str = "SourceConcept"
    to_type: str = "SourceConcept"
    relationship: str = "HAS_PARENT"
    directed: bool = True


class ConceptHasSourceConcept(BaseEdge):
    from_type: str = "Concept"
    to_type: str = "SourceConcept"
    relationship: str = "HAS_SOURCE_CONCEPT"
    directed: bool = True


class SourceConceptHasFieldOfWork(BaseEdge):
    from_type: str = "SourceName"
    to_type: str = "SourceConcept"
    relationship: str = "HAS_FIELD_OF_WORK"
    directed: bool = True


class WorkHasConcept(BaseEdge):
    from_type: str = "Work"
    to_type: str = "Concept"
    relationship: str = "HAS_CONCEPT"
    directed: bool = True
