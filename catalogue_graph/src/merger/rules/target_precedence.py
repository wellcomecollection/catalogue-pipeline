"""Which work in a merge group becomes the target the others are merged into."""

from collections.abc import Sequence

from merger.rules.predicates import (
    Predicate,
    any_work,
    ebsco_work,
    lms_work,
    lms_work_digitised_av,
    physical_lms_work,
    single_physical_item_cms_work,
    tei_work,
)
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork

# The order in which target works are tried.
PRECEDENCE = (
    ebsco_work,
    tei_work,
    single_physical_item_cms_work,
    lms_work_digitised_av,
    physical_lms_work,
    lms_work,
)


def choose_target(
    works: Sequence[IdentifiedWork], satisfying: Predicate = any_work
) -> VisibleIdentifiedWork | None:
    """The first work, in precedence order, that also satisfies the predicate; only if visible."""
    for precedence in PRECEDENCE:
        for work in works:
            if precedence(work) and satisfying(work):
                return work if isinstance(work, VisibleIdentifiedWork) else None
    return None
