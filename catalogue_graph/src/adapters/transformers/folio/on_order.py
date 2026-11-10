"""The placeholder item for a FOLIO instance that is awaiting cataloguing (platform#6778)."""

from pymarc.record import Record

from models.pipeline.identifier import Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import OnOrder, PhysicalLocation

# Marks an acquisitions bib with no items. Removed on cataloguing (platform#6765).
UNCATALOGUED_NOTE_PREFIX = "UNCATALOGUED"
AWAITING_CATALOGUING_LABEL = "Awaiting cataloguing for Wellcome Collection"


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
        locations=[
            PhysicalLocation(
                location_type=OnOrder,
                label=AWAITING_CATALOGUING_LABEL,
                access_conditions=[],
            )
        ],
    )
