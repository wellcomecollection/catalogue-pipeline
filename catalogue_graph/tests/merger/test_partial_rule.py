"""A partial rule applies only to targets and sources that satisfy its predicates."""

from merger.rules.base import PartialRule
from merger.rules.predicates import Predicate, any_work
from models import identifier_schemes as schemes
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from tests.merger.works import identified_work


def title_contains(letter: str) -> Predicate:
    return Predicate(lambda work: letter in (work.data.title or ""))


class TitleRule(PartialRule[None]):
    def __init__(self, target: Predicate, source: Predicate):
        self.is_defined_for_target = target
        self.is_defined_for_source = source
        self.seen: list[IdentifiedWork] = []

    def rule(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> None:
        self.seen = sources


work_a = identified_work(schemes.SIERRA_SYSTEM_NUMBER, title="A")
work_b = identified_work(schemes.SIERRA_SYSTEM_NUMBER, title="B")


def test_defined_only_for_a_target_satisfying_its_target_predicate() -> None:
    rule = TitleRule(target=title_contains("A"), source=any_work)

    assert rule.merged_sources(work_a, [work_b])
    assert not rule.merged_sources(work_b, [work_b])


def test_defined_only_if_at_least_one_source_satisfies_its_source_predicate() -> None:
    rule = TitleRule(target=any_work, source=title_contains("A"))

    assert rule.merged_sources(work_b, [work_a, work_b])
    assert not rule.merged_sources(work_b, [work_b])


def test_calls_its_rule_with_only_the_sources_satisfying_the_source_predicate() -> None:
    rule = TitleRule(target=any_work, source=title_contains("A"))

    rule(work_b, [work_a, work_b])

    assert rule.seen == [work_a]
