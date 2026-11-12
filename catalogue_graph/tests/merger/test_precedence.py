"""Choosing the target of a merge group."""

from merger.rules.precedence import first_by_precedence
from merger.rules.predicates import single_item_lms_work
from models.pipeline.format import Videos
from tests.merger.works import (
    axiell_work,
    calm_work,
    deleted,
    ebsco_work,
    invisible,
    miro_work,
    physical_item,
    sierra_digital_work,
    sierra_physical_work,
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

    assert first_by_precedence(works) == tei


def test_second_a_calm_work() -> None:
    works = [video_sierra, multi_item_physical_sierra, digital_sierra, calm, miro]

    assert first_by_precedence(works) == calm


def test_second_an_axiell_work_at_the_same_precedence_as_calm() -> None:
    works = [video_sierra, multi_item_physical_sierra, digital_sierra, axiell, miro]

    assert first_by_precedence(works) == axiell


def test_third_a_sierra_e_video() -> None:
    works = [multi_item_physical_sierra, digital_sierra, miro, video_sierra]

    assert first_by_precedence(works) == video_sierra


def test_fourth_a_physical_sierra_work() -> None:
    works = [digital_sierra, multi_item_physical_sierra, miro]

    assert first_by_precedence(works) == multi_item_physical_sierra


def test_finally_any_remaining_sierra_work() -> None:
    assert first_by_precedence([miro, digital_sierra]) == digital_sierra


def test_the_first_of_several_works_with_the_same_precedence() -> None:
    # Documents existing behaviour; revise if a better way of choosing is defined.
    digital_sierra_2 = sierra_digital_work()

    assert (
        first_by_precedence([miro, digital_sierra, digital_sierra_2]) == digital_sierra
    )
    assert (
        first_by_precedence([digital_sierra_2, miro, digital_sierra])
        == digital_sierra_2
    )


def test_nothing_when_no_valid_target_is_present() -> None:
    assert first_by_precedence([miro]) is None


def test_an_additional_predicate_narrows_the_choice() -> None:
    works = [multi_item_physical_sierra, digital_sierra, miro]

    assert first_by_precedence(works) == multi_item_physical_sierra
    assert first_by_precedence(works, satisfying=single_item_lms_work) == digital_sierra


def test_an_ebsco_work_before_all_others() -> None:
    ebsco = ebsco_work()
    works = [calm, video_sierra, tei, multi_item_physical_sierra, digital_sierra, miro]

    assert first_by_precedence([*works, ebsco]) == ebsco


def test_nothing_when_the_first_matching_work_is_invisible() -> None:
    # Only the chosen work is checked for visibility, as in the Scala merger.
    invisible_physical_sierra = invisible(sierra_physical_work())
    invisible_digital_sierra = invisible(sierra_digital_work())

    assert first_by_precedence([invisible_physical_sierra, digital_sierra]) is None
    assert first_by_precedence([digital_sierra, invisible_physical_sierra]) is None
    assert first_by_precedence([invisible_digital_sierra, digital_sierra]) is None
    assert (
        first_by_precedence([digital_sierra, invisible_digital_sierra])
        == digital_sierra
    )


def test_nothing_when_the_first_matching_work_is_deleted() -> None:
    deleted_ebsco = deleted(ebsco_work())
    deleted_digital_sierra = deleted(sierra_digital_work())

    assert first_by_precedence([tei, deleted_ebsco]) is None
    assert first_by_precedence([deleted_digital_sierra, digital_sierra]) is None
    assert (
        first_by_precedence([digital_sierra, deleted_digital_sierra]) == digital_sierra
    )
