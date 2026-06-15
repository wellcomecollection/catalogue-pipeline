from models.pipeline.access_condition import (
    AccessCondition,
    AccessMethod,
    AccessStatus,
)
from models.pipeline.access_status import AccessStatusValue
from models.pipeline.identifier import SourceIdentifier, Unidentifiable
from models.pipeline.item import Item

from .identifiers import create_identified
from .locations import create_digital_location, create_physical_location


def create_item(
    locations: list | None = None,
    other_identifiers: list[SourceIdentifier] | None = None,
) -> Item:
    if locations is None:
        locations = [create_digital_location(location_type_id="iiif-image")]
    item_id = create_identified(other_identifiers=other_identifiers)
    return Item(id=item_id, locations=locations)


def create_unidentifiable_item(locations: list | None = None) -> Item:
    if locations is None:
        locations = [create_digital_location(location_type_id="iiif-image")]

    return Item(id=Unidentifiable(), locations=locations)


def create_digital_item(
    license_id: str = "cc-by",
    access_status_type: AccessStatusValue | None = None,
) -> Item:
    access_conditions: list[AccessCondition] = []
    if access_status_type:
        access_conditions = [
            AccessCondition(
                method=AccessMethod(type="ViewOnline"),
                status=AccessStatus(type=access_status_type),
            )
        ]
    loc = create_digital_location(
        location_type_id="iiif-image",
        license_id=license_id,
        access_conditions=access_conditions,
    )
    return create_unidentifiable_item(locations=[loc])


def create_closed_stores_item() -> Item:
    loc = create_physical_location(location_type_id="closed-stores")
    return Item(id=create_identified(), locations=[loc])


def create_open_shelves_item() -> Item:
    loc = create_physical_location(location_type_id="open-shelves")
    return Item(id=create_identified(), locations=[loc])
