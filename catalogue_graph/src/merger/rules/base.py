"""The shape of a merge rule.

A field rule computes one field of the merged target from the target and its sources,
and reports which sources it drew on; those sources are redirected to the target. Most
rules are built from partial rules, each applying only when the target and at least one
source satisfy its predicates. Rules hold no state, so they are used as classes.
"""

from abc import ABC, abstractmethod
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from typing import Any

from merger.rules.predicates import Predicate
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork


@dataclass(frozen=True)
class FieldMergeResult[T]:
    data: T
    redirected_sources: list[IdentifiedWork]


class FieldMergeRule[T](ABC):
    @classmethod
    @abstractmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[T]: ...


class PartialRule[T](ABC):
    accepts_target: Predicate
    accepts_source: Predicate

    @classmethod
    @abstractmethod
    def rule(cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]) -> T:
        """The merged field value; only called with a non-empty list of matching sources."""

    @classmethod
    def merged_sources(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        if cls.accepts_target(target):
            return [source for source in sources if cls.accepts_source(source)]
        return []

    @classmethod
    def apply(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> T | None:
        """The merged field value, or None when the rule does not apply."""
        merged = cls.merged_sources(target, sources)
        return cls.rule(target, merged) if merged else None


class SingleSource[T](PartialRule[T]):
    """Accepts a single source. Several accepted sources are ambiguous, so none is merged."""

    @classmethod
    def merged_sources(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        merged = super().merged_sources(target, sources)
        return merged if len(merged) == 1 else []


def apply_first_applicable[T](
    rules: Sequence[type[PartialRule[T]]],
    target: VisibleIdentifiedWork,
    sources: Sequence[IdentifiedWork],
) -> T | None:
    """The value of the first partial rule that applies."""
    for rule in rules:
        value = rule.apply(target, sources)
        if value is not None:
            return value
    return None


def sources_to_redirect(
    rules: Sequence[type[PartialRule[Any]]],
    target: VisibleIdentifiedWork,
    sources: Sequence[IdentifiedWork],
) -> list[IdentifiedWork]:
    return [s for rule in rules for s in rule.merged_sources(target, sources)]


def distinct_works(works: Iterable[IdentifiedWork]) -> list[IdentifiedWork]:
    return list({work.state.canonical_id: work for work in works}.values())
