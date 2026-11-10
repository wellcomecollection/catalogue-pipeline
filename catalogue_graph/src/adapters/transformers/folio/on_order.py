"""Items for material on order or awaiting cataloguing (platform#6778, platform#6798)."""

from pymarc.record import Record

from models.pipeline.identifier import Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import OnOrder, PhysicalLocation

# Marks an acquisitions bib with no items. Removed on cataloguing (platform#6765).
UNCATALOGUED_NOTE_PREFIX = "UNCATALOGUED"
ORDERED_LABEL = "Ordered for Wellcome Collection"
AWAITING_CATALOGUING_LABEL = "Awaiting cataloguing for Wellcome Collection"

# An item created by a FOLIO order starts at On order and moves to In process when
# received. Matched exactly: "In process (non-requestable)" is a digitisation status.
ON_ORDER_STATUS_LABELS = {
    "On order": ORDERED_LABEL,
    "In process": AWAITING_CATALOGUING_LABEL,
}


def on_order_location(label: str) -> PhysicalLocation:
    """The on-order location, which has no shelfmark or access conditions."""
    return PhysicalLocation(location_type=OnOrder, label=label, access_conditions=[])


def on_order_label(status: str | None) -> str | None:
    """The on-order label for an item status, or None when the item is not on order."""
    return ON_ORDER_STATUS_LABELS.get(status or "")


def is_uncatalogued(record: Record) -> bool:
    """Whether any 590 ǂa marks the instance as uncatalogued."""
    return any(
        value.strip().startswith(UNCATALOGUED_NOTE_PREFIX)
        for field in record.get_fields("590")
        for value in field.get_subfields("a")
    )


def awaiting_cataloguing_item() -> Item:
    """The placeholder item shown while the material awaits cataloguing."""
    return Item(
        id=Unidentifiable(),
        locations=[on_order_location(AWAITING_CATALOGUING_LABEL)],
    )
