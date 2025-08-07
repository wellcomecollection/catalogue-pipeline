from pydantic import BaseModel

from .node import ConceptNode, SourceConceptNode, WorkNode


class WorkHierarchyChild(BaseModel):
    work: WorkNode
    parts: int


class WorkHierarchy(BaseModel):
    id: str
    ancestor_works: list[WorkNode] = []
    children: list[WorkHierarchyChild] = []


class WorkConcept(BaseModel):
    concept: ConceptNode
    linked_source_concept: SourceConceptNode | None
    other_source_concepts: list[SourceConceptNode]
