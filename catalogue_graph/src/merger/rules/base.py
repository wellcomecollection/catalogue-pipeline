"""The shape of a merge rule.

A rule merges some sources into some targets. It names the targets and sources it
accepts, claims the acceptable sources of a merge, and computes one field's value from
them. Claimed sources are redirected to the target whether or not that rule's value is
the one used, so a field is merged by asking every rule what it claims and taking the
value of the first rule that applies. Rules hold no state, so they are used as classes.
"""

from abc import ABC, abstractmethod
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from typing import Any

from merger.rules.predicates import Predicate
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork


@dataclass(frozen=True)
class MergedField[T]:
    """A field's merged value, and the sources it was drawn from."""

    value: T
    claimed: list[IdentifiedWork]


class Rule[T](ABC):
    accepts_target: Predicate
    accepts_source: Predicate

    @classmethod
    def claims(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        """The sources this rule would merge into this target."""
        if cls.accepts_target(target):
            return [source for source in sources if cls.accepts_source(source)]
        return []

    @classmethod
    @abstractmethod
    def value(cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]) -> T:
        """The field value from the claimed sources; never called without any."""

    @classmethod
    def apply(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> MergedField[T] | None:
        """The merged field, or None when the rule claims nothing."""
        claimed = cls.claims(target, sources)
        return MergedField(cls.value(target, claimed), claimed) if claimed else None


class SingleSource[T](Rule[T]):
    """Claims a single source. Several acceptable sources are ambiguous, so none is claimed."""

    @classmethod
    def claims(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        claimed = super().claims(target, sources)
        return claimed if len(claimed) == 1 else []


def first_applicable[T](
    rules: Sequence[type[Rule[T]]],
    target: VisibleIdentifiedWork,
    sources: Sequence[IdentifiedWork],
) -> MergedField[T] | None:
    """The merged field of the first rule that applies."""
    for rule in rules:
        merged = rule.apply(target, sources)
        if merged is not None:
            return merged
    return None


def claimed_by(
    rules: Sequence[type[Rule[Any]]],
    target: VisibleIdentifiedWork,
    sources: Sequence[IdentifiedWork],
) -> list[IdentifiedWork]:
    """The sources claimed by any of the rules."""
    return distinct_works(s for rule in rules for s in rule.claims(target, sources))


def distinct_works(works: Iterable[IdentifiedWork]) -> list[IdentifiedWork]:
    return list({work.state.canonical_id: work for work in works}.values())
