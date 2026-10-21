"""
Extract the publish-to-web markers from local fields emitted by the OAI stylesheet
981 - $a - 'yes' or 'no', from the Axiell publish_to_web checkbox
985 - $a - 'x' when the "for deletion?" free checkbox is ticked, absent otherwise
      (983 is the Parts child-link field, not a marker)
"""

from typing import Literal

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields

PublishToWeb = Literal["yes", "no"]


def extract_publish_to_web(record: Record) -> PublishToWeb | None:
    """Raw 981 $a marker: 'yes', 'no', or None when absent or unrecognised.

    The stylesheet emits the marker on every record, so None means a pre-marker
    or anomalous harvest. Use is_publishable for the publish decision.
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


def is_publishable(record: Record) -> bool:
    """Only an explicit 'yes' publishes; anything else, including an absent marker, fails closed."""
    return extract_publish_to_web(record) == "yes"


def is_marked_for_deletion(record: Record) -> bool:
    """A ticked "for deletion?" checkbox suppresses the record whatever 981 says."""
    return bool(non_empty_subfields("985", "a", record))
