"""The shape of a merge rule.

A field rule computes one field of the merged target from the target and its sources,
and reports which sources it drew on; those sources are redirected to the target. Most
rules are built from partial rules, each applying only when the target and at least one
source satisfy its predicates.
"""

from abc import ABC, abstractmethod
from collections.abc import Iterable, Sequence
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
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[T]: ...

    def __call__(
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[T]:
        return self.merge(target, sources)


class PartialRule[T](ABC):
    is_defined_for_target: Predicate
    is_defined_for_source: Predicate

    def is_defined_for_source_list(self, sources: Sequence[IdentifiedWork]) -> bool:
        return True

    @abstractmethod
    def rule(self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]) -> T:
        """The merged field value; only called with a non-empty list of matching sources."""

    def merged_sources(
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        if self.is_defined_for_source_list(sources) and self.is_defined_for_target(
            target
        ):
            return [source for source in sources if self.is_defined_for_source(source)]
        return []

    def __call__(
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> T | None:
        merged = self.merged_sources(target, sources)
        return self.rule(target, merged) if merged else None


def first_defined[T](
    rules: Sequence[PartialRule[T]],
    target: VisibleIdentifiedWork,
    sources: Sequence[IdentifiedWork],
) -> T | None:
    """The value of the first partial rule that applies."""
    for rule in rules:
        value = rule(target, sources)
        if value is not None:
            return value
    return None


def distinct_works(works: Iterable[IdentifiedWork]) -> list[IdentifiedWork]:
    seen: set[str] = set()
    distinct = []
    for work in works:
        if work.state.canonical_id not in seen:
            seen.add(work.state.canonical_id)
            distinct.append(work)
    return distinct
