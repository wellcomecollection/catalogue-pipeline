"""
Extract the publish-to-web marker from local field
981 - Local field, emitted by the OAI stylesheet
    $a - 'yes' or 'no', from the Axiell publish_to_web checkbox
"""

from typing import Literal

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields

PublishToWeb = Literal["yes", "no"]


def extract_publish_to_web(record: Record) -> PublishToWeb | None:
    """Extract the explicit publish-to-web marker from 981 $a.

    Returns None when the marker is absent or carries an unexpected value.
    The stylesheet emits the marker on every record, so None means a pre-marker
    or anomalous harvest; callers must fail closed and treat only an explicit
    'yes' as publishable.
    """
    values = non_empty_subfields("981", "a", record)
    if not values:
        return None

    value = values[0].strip().lower()
    if value == "yes":
        return "yes"
    if value == "no":
        return "no"
    return None
