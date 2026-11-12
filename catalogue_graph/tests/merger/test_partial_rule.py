"""A partial rule applies only to targets and sources that satisfy its predicates."""

from typing import ClassVar

from merger.rules.base import PartialRule
from merger.rules.predicates import Predicate, any_work
from models import identifier_schemes as schemes
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from tests.merger.works import identified_work


def title_contains(letter: str) -> Predicate:
    return Predicate(lambda work: letter in (work.data.title or ""))


class RecordingRule(PartialRule[None]):
    seen: ClassVar[list[IdentifiedWork]] = []

    @classmethod
    def rule(cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]) -> None:
        cls.seen = sources


class TargetTitleIsA(RecordingRule):
    accepts_target = title_contains("A")
    accepts_source = any_work


class SourceTitleIsA(RecordingRule):
    accepts_target = any_work
    accepts_source = title_contains("A")


work_a = identified_work(schemes.SIERRA_SYSTEM_NUMBER, title="A")
work_b = identified_work(schemes.SIERRA_SYSTEM_NUMBER, title="B")


def test_defined_only_for_a_target_satisfying_its_target_predicate() -> None:
    assert TargetTitleIsA.merged_sources(work_a, [work_b])
    assert not TargetTitleIsA.merged_sources(work_b, [work_b])


def test_defined_only_if_at_least_one_source_satisfies_its_source_predicate() -> None:
    assert SourceTitleIsA.merged_sources(work_b, [work_a, work_b])
    assert not SourceTitleIsA.merged_sources(work_b, [work_b])


def test_calls_its_rule_with_only_the_sources_satisfying_the_source_predicate() -> None:
    SourceTitleIsA.apply(work_b, [work_a, work_b])

    assert SourceTitleIsA.seen == [work_a]
