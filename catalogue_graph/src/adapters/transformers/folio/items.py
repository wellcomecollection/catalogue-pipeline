"""Build catalogue items from a FOLIO instance's items-and-holdings record."""

from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioEnrichedItem,
)
from adapters.transformers.folio.access_conditions import (
    FolioItemData,
    evaluate,
    is_access_note,
)
from adapters.transformers.folio.locations import ResolvedLocation, resolve_locations
from adapters.transformers.folio.on_order import on_order_label, on_order_location
from models.pipeline.access_condition import AccessCondition
from models.pipeline.identifier import Id, Identifiable, SourceIdentifier
from models.pipeline.item import Item
from models.pipeline.location import PhysicalLocation

# The source-identifier type for a FOLIO item. The id-minter turns this plus the
# item UUID into a stable canonical id for the public catalogue.
FOLIO_ITEM_IDENTIFIER_TYPE = Id(id="folio-item")


def extract_items(instance: FolioEnrichedInstance) -> list[Item]:
    # Suppressed items are never shown, nor do they type a bound-with item.
    items = [item for item in instance.items if not item.suppress_from_discovery]
    locations = resolve_locations(items)
    return [_build_item(item, locations[item.id]) for item in items]


def _build_item(
    item: FolioEnrichedItem, item_location: ResolvedLocation | None
) -> Item:
    identifier = Identifiable.from_source_identifier(
        SourceIdentifier(
            identifier_type=FOLIO_ITEM_IDENTIFIER_TYPE,
            ontology_type="Item",
            value=item.id,
        )
    )
    title = item.enumeration or item.volume
    if (label := on_order_label(item.status)) is not None:
        # Still being acquired, so its location and access condition don't apply yet.
        return Item(
            id=identifier,
            title=title,
            note=item.display_note,
            locations=[on_order_location(label)],
        )
    if item_location is None:
        # No location to carry an access condition, so the display note stays on the item.
        return Item(id=identifier, title=title, note=item.display_note)

    location = item.effective_location
    # A resolved location, own or bound-with, always comes from an effective location.
    assert location is not None
    access_condition = evaluate(
        FolioItemData(
            item_status=item.status,
            statistical_code=item.statistical_codes[0].code
            if item.statistical_codes
            else None,
            # FOLIO circulation applies the temporary loan type when one is set.
            loan_type=item.temporary_loan_type or item.permanent_loan_type,
            library=location.library_name,
            location_code=location.code,
            location_type=item_location.location_type.id,
            material_type=item.material_type,
            display_note=item.display_note,
        )
    )
    return Item(
        id=identifier,
        title=title,
        note=_item_note(item.display_note, access_condition),
        locations=[
            PhysicalLocation(
                location_type=item_location.location_type,
                label=item_location.label,
                shelfmark=item.call_number,
                access_conditions=[access_condition],
            )
        ],
    )


def _item_note(display_note: str | None, condition: AccessCondition) -> str | None:
    # A display note about access belongs to the access condition, used or dropped.
    if (
        display_note is None
        or display_note == condition.note
        or is_access_note(display_note)
    ):
        return None
    return display_note
