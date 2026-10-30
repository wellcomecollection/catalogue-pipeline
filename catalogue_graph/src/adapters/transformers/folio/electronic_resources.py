"""Digital items from the instance 856 (Electronic Location and Access).

Links live only on the instance in FOLIO: holdings and items carry no electronic
access, and the local 956 (Wellcome digitised links) stays unmapped, as digitised
copies reach a work through METS (platform#6766).
"""

import re

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_blank_subfields, non_empty
from adapters.transformers.utils.html import is_url
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import ViewOnline
from models.pipeline.access_status import (
    AccessStatus,
    LicensedRelatedResource,
    LicensedResource,
)
from models.pipeline.identifier import Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation, OnlineResource

logger = structlog.get_logger(__name__)

# ǂz public note, ǂy link text and ǂ3 materials specified are all used as the
# human-readable label at Wellcome, in whichever order they appear.
LABEL_SUBFIELDS = ("z", "y", "3")

# The 856 labels are inconsistent in intent: some name the resource, others
# invite the reader to access it, and they vary from terse to verbose. This is a
# heuristic for telling them apart. A short label that invites the reader to do
# something is a call to action, so it becomes the location's link text. Anything
# else names the resource and becomes the item title, which keeps titles and
# link texts distinct for display.
LINK_TEXT_MAX_WORDS = 7
CALL_TO_ACTION_WORDS = ("access", "view", "connect")

# Fixes for a handful of known records, cheaper here than in the catalogue.
LINK_TEXT_FIXES = {
    "VIEW FULL TEXT": "View full text",
    "via  MyiLibrary": "via MyiLibrary",
    "youtube": "YouTube",
    "View resource {PDF": "View resource [PDF",
    "View resource 613.7 KB]": "View resource [613.7 KB]",
}


def extract_electronic_resources(record: Record) -> list[Item]:
    return non_empty(_to_item(field) for field in record.get_fields("856"))


def _to_item(field: Field) -> Item | None:
    url = _get_url(field)
    if url is None:
        return None
    title, link_text = _split_label(_get_label(field))
    return Item(
        id=Unidentifiable(),
        title=title,
        locations=[
            DigitalLocation(
                url=url,
                link_text=link_text,
                location_type=OnlineResource,
                # The status is what puts the work in the "available online" filter
                # (platform#5062).
                access_conditions=[
                    AccessCondition(method=ViewOnline, status=_status(field))
                ],
            )
        ],
    )


def _get_url(field: Field) -> str | None:
    """The first URL in ǂu, or None when there is no resolvable one.

    ǂu is repeatable in MARC but means one resource here, so a repeat takes the
    first valid value and logs a warning.
    """
    values = [value.strip() for value in non_blank_subfields(field, "u")]
    if not values:
        logger.warning("856 without a URL", field=str(field))
        return None
    if len(values) > 1:
        logger.warning("Repeated 856 ǂu", field=str(field))
    urls = []
    for value in values:
        if is_url(value):
            urls.append(value)
        else:
            logger.warning("856 ǂu is not a URL", value=value)
    return urls[0] if urls else None


def _get_label(field: Field) -> str:
    return " ".join(
        value.strip() for value in non_blank_subfields(field, *LABEL_SUBFIELDS)
    )


def _split_label(label: str) -> tuple[str | None, str | None]:
    """Return (title, link_text), with the label in exactly one of them if present."""
    if not label:
        return None, None
    is_short = len(label.split(" ")) <= LINK_TEXT_MAX_WORDS
    if is_short and any(word in label.lower() for word in CALL_TO_ACTION_WORDS):
        return None, _tidy_link_text(label)
    return label, None


def _tidy_link_text(label: str) -> str:
    text = re.sub(r"^view ", "View ", label.removesuffix(".").removesuffix(":"))
    for old, new in LINK_TEXT_FIXES.items():
        text = text.replace(old, new)
    return text


def _status(field: Field) -> AccessStatus:
    """ind2 = 2 marks a related resource, such as a publisher's description."""
    if field.indicator2 == "2":
        return LicensedRelatedResource
    return LicensedResource
