from pydantic import BaseModel

from utils.types import ConceptType

from .node import ConceptNode, SourceConceptNode, WorkNode


class WorkHierarchyItem(BaseModel):
    work: WorkNode
    parts: int


class WorkHierarchy(BaseModel):
    id: str
    ancestors: list[WorkHierarchyItem] = []
    children: list[WorkHierarchyItem] = []


class ExtractedConcept(BaseModel):
    concept: ConceptNode
    linked_source_concept: SourceConceptNode | None
    source_concepts: list[SourceConceptNode]
    types: list[ConceptType] = []
    same_as: list[str] = []


class ExtractedRelatedConcept(BaseModel):
    target: ExtractedConcept
    relationship_type: str | None
