from functools import cached_property

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

    def standardise_title(self, title: str) -> str:
        return title.rstrip(".")

    @cached_property
    def standard_ancestor_titles(self) -> set[str]:
        titles = set()
        for ancestor in self.ancestors:
            if (title := ancestor.work.properties.label) is not None:
                titles.add(self.standardise_title(title))

        return titles

    def ancestors_include_title(self, title: str) -> bool:
        return self.standardise_title(title) in self.standard_ancestor_titles


class WorkConcept(BaseModel):
    concept: ConceptNode
    linked_source_concept: SourceConceptNode | None
    other_source_concepts: list[SourceConceptNode]


class ExtractedConcept(BaseModel):
    concept: ConceptNode
    linked_source_concept: SourceConceptNode | None
    source_concepts: list[SourceConceptNode]
    types: list[ConceptType]
    same_as: list[str]


class ExtractedRelatedConcept(BaseModel):
    target: ExtractedConcept
    relationship_type: str | None
