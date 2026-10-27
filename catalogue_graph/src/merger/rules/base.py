"""The shape of a merge rule, ported from the Scala FieldMergeRule.

A field rule computes one field of the merged target from the target and its sources,
and reports which sources it drew on; those sources are redirected to the target. Most
rules are built from partial rules, each applying only when the target and at least one
source satisfy its predicates.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass

from merger.rules.predicates import Predicate
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork


@dataclass(frozen=True)
class FieldMergeResult[T]:
    data: T
    sources: list[IdentifiedWork]


class FieldMergeRule[T](ABC):
    @abstractmethod
    def merge(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> FieldMergeResult[T]: ...

    def __call__(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> FieldMergeResult[T]:
        return self.merge(target, sources)


class PartialRule[T](ABC):
    is_defined_for_target: Predicate
    is_defined_for_source: Predicate

    def is_defined_for_source_list(self, sources: list[IdentifiedWork]) -> bool:
        return True

    @abstractmethod
    def rule(self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]) -> T:
        """The merged field value; only called with a non-empty list of matching sources."""

    def merged_sources(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        if self.is_defined_for_source_list(sources) and self.is_defined_for_target(
            target
        ):
            return [source for source in sources if self.is_defined_for_source(source)]
        return []

    def __call__(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> T | None:
        merged = self.merged_sources(target, sources)
        return self.rule(target, merged) if merged else None
