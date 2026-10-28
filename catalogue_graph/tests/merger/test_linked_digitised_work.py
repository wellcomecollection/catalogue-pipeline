"""Finding the digitised bib paired with a physical LMS work."""

from merger.rules.predicates import PHYSICAL_DIGITAL_REASON
from merger.rules.support import find_first_linked_digitised_lms_work
from models import identifier_schemes as schemes
from models.pipeline.format import Books, Videos
from models.pipeline.identified.work import IdentifiedMergeCandidate
from models.pipeline.identifier import Identified
from tests.merger.works import (
    calm_work,
    merge_candidate,
    miro_work,
    sierra_digital_work,
    sierra_physical_work,
    source_identifier,
)

MIRO_SIERRA_REASON = "Miro/Sierra work"


def test_nothing_without_sources() -> None:
    physical = sierra_physical_work()

    assert find_first_linked_digitised_lms_work(physical, []) is None


def test_the_first_source_with_a_matching_merge_candidate() -> None:
    digitised_1 = sierra_digital_work()
    digitised_2 = sierra_digital_work()
    physical = sierra_physical_work(
        merge_candidates=[
            merge_candidate(digitised_1, PHYSICAL_DIGITAL_REASON),
            merge_candidate(digitised_2, PHYSICAL_DIGITAL_REASON),
        ]
    )

    result = find_first_linked_digitised_lms_work(physical, [digitised_1, digitised_2])

    assert result == digitised_1


def test_a_merge_candidate_on_the_digitised_work() -> None:
    # Some bib/e-bib pairs carry the 776 $w link on the electronic bib.
    physical = sierra_physical_work()
    digitised = sierra_digital_work(
        merge_candidates=[merge_candidate(physical, PHYSICAL_DIGITAL_REASON)]
    )

    assert find_first_linked_digitised_lms_work(physical, [digitised]) == digitised


def test_skips_a_merge_candidate_with_the_right_id_but_wrong_reason() -> None:
    digitised = sierra_digital_work()
    physical = sierra_physical_work(
        merge_candidates=[merge_candidate(digitised, "Linked for a mystery reason")]
    )

    assert find_first_linked_digitised_lms_work(physical, [digitised]) is None


def test_skips_a_merge_candidate_with_the_right_reason_but_wrong_id() -> None:
    digitised = sierra_digital_work()
    physical = sierra_physical_work(
        merge_candidates=[
            IdentifiedMergeCandidate(
                id=Identified(
                    canonical_id="elsewhere",
                    source_identifier=source_identifier(schemes.SIERRA_IDENTIFIER),
                ),
                reason=PHYSICAL_DIGITAL_REASON,
            )
        ]
    )

    assert find_first_linked_digitised_lms_work(physical, [digitised]) is None


def test_skips_a_target_that_is_not_an_lms_work() -> None:
    digitised = sierra_digital_work()
    physical = calm_work(
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)]
    )

    assert find_first_linked_digitised_lms_work(physical, [digitised]) is None


def test_skips_a_target_that_is_not_a_physical_lms_work() -> None:
    digitised = sierra_digital_work()
    physical = sierra_digital_work(
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)]
    )

    assert find_first_linked_digitised_lms_work(physical, [digitised]) is None


def test_skips_an_audiovisual_target() -> None:
    digitised = sierra_digital_work()
    physical = sierra_physical_work(
        format=Videos,
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)],
    )

    assert find_first_linked_digitised_lms_work(physical, [digitised]) is None


def test_a_candidate_on_the_e_bib_when_the_physical_bib_has_an_unrelated_one() -> None:
    miro = miro_work()
    physical = sierra_physical_work(
        format=Books, merge_candidates=[merge_candidate(miro, MIRO_SIERRA_REASON)]
    )
    digitised = sierra_digital_work(
        merge_candidates=[merge_candidate(physical, PHYSICAL_DIGITAL_REASON)]
    )

    result = find_first_linked_digitised_lms_work(physical, [digitised, miro])

    assert result == digitised
