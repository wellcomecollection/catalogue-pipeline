from functools import cached_property

from pydantic import BaseModel

from utils.types import ConceptType

from .node import ConceptNode, SourceConceptNode, WorkNode


def _standardise_work_title(title: str) -> str:
    return title.rstrip(".")


class WorkHierarchyItem(BaseModel):
    work: WorkNode
    parts: int


class WorkHierarchy(BaseModel):
    id: str
    ancestors: list[WorkHierarchyItem] = []
    children: list[WorkHierarchyItem] = []

    @cached_property
    def _ancestor_titles(self) -> set[str]:
        titles = set()
        for ancestor in self.ancestors:
            if (title := ancestor.work.properties.label) is not None:
                titles.add(_standardise_work_title(title))

        return titles

    def ancestors_include_title(self, title: str) -> bool:
        """Returns true if a given title is used by one of the work's ancestors."""
        return _standardise_work_title(title) in self._ancestor_titles


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
