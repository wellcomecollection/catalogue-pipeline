from models.pipeline.access_condition import AccessCondition
from models.pipeline.id_label import Id
from models.pipeline.location import DigitalLocation, LocationType, PhysicalLocation

from .random import random_alphanumeric, rng


def create_physical_location(
    location_type_id: str | None = None,
    label: str = "locationLabel",
    license_id: str | None = None,
    access_conditions: list[AccessCondition] | None = None,
    shelfmark: str | None = None,
) -> PhysicalLocation:
    location_type_id = location_type_id or rng.choice(["closed-stores", "open-shelves"])
    license_id = license_id or rng.choice([None, "cc-by", "ogl", "pdm"])
    shelfmark = shelfmark or rng.choice([None, f"Shelfmark: {random_alphanumeric()}"])

    return PhysicalLocation(
        location_type=LocationType(id=location_type_id),
        label=label,
        license=None if license_id is None else Id(id=license_id),
        shelfmark=shelfmark,
        access_conditions=access_conditions or [],
    )


def create_digital_location(
    location_type_id: str,
    url: str | None = None,
    license_id: str = "cc-by",
    access_conditions: list[AccessCondition] | None = None,
    created_date: str | None = None,
) -> DigitalLocation:
    url_id = random_alphanumeric(3)
    default_url = f"https://iiif.wellcomecollection.org/image/{url_id}.jpg/info.json"

    return DigitalLocation(
        location_type=LocationType(id=location_type_id),
        url=url or default_url,
        license=Id(id=license_id),
        link_text=rng.choice([None, f"Link text: {random_alphanumeric()}"]),
        credit=rng.choice([None, f"Credit line: {random_alphanumeric()}"]),
        access_conditions=access_conditions or [],
        created_date=created_date,
    )
