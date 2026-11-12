"""Choosing the target of a merge group."""

from merger.rules.predicates import single_item_lms_work
from merger.rules.target_precedence import choose_target
from models.pipeline.format import Videos
from tests.merger.works import (
    axiell_work,
    calm_work,
    miro_work,
    physical_item,
    sierra_digital_work,
    sierra_work,
    tei_work,
)

tei = tei_work()
calm = calm_work()
axiell = axiell_work()
video_sierra = sierra_digital_work(format=Videos)
multi_item_physical_sierra = sierra_work(items=[physical_item(), physical_item()])
digital_sierra = sierra_digital_work()
miro = miro_work()


def test_first_a_tei_work() -> None:
    works = [calm, video_sierra, tei, multi_item_physical_sierra, digital_sierra, miro]

    assert choose_target(works) == tei


def test_second_a_calm_work() -> None:
    works = [video_sierra, multi_item_physical_sierra, digital_sierra, calm, miro]

    assert choose_target(works) == calm


def test_second_an_axiell_work_at_the_same_precedence_as_calm() -> None:
    works = [video_sierra, multi_item_physical_sierra, digital_sierra, axiell, miro]

    assert choose_target(works) == axiell


def test_third_a_sierra_e_video() -> None:
    works = [multi_item_physical_sierra, digital_sierra, miro, video_sierra]

    assert choose_target(works) == video_sierra


def test_fourth_a_physical_sierra_work() -> None:
    works = [digital_sierra, multi_item_physical_sierra, miro]

    assert choose_target(works) == multi_item_physical_sierra


def test_finally_any_remaining_sierra_work() -> None:
    assert choose_target([miro, digital_sierra]) == digital_sierra


def test_the_first_of_several_works_with_the_same_precedence() -> None:
    # Documents existing behaviour; revise if a better way of choosing is defined.
    digital_sierra_2 = sierra_digital_work()

    assert choose_target([miro, digital_sierra, digital_sierra_2]) == digital_sierra
    assert choose_target([digital_sierra_2, miro, digital_sierra]) == digital_sierra_2


def test_nothing_when_no_valid_target_is_present() -> None:
    assert choose_target([miro]) is None


def test_an_additional_predicate_narrows_the_choice() -> None:
    works = [multi_item_physical_sierra, digital_sierra, miro]

    assert choose_target(works) == multi_item_physical_sierra
    assert choose_target(works, satisfying=single_item_lms_work) == digital_sierra
