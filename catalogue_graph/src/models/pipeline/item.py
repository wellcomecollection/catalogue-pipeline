from models.pipeline.identifier import Identifiable, Identified, Unidentifiable
from models.pipeline.location import DigitalLocation, PhysicalLocation
from models.pipeline.serialisable import SerialisableModel


class Item(SerialisableModel):
    # `Identifiable` covers the source stage, before the id-minter has assigned a
    # canonical id (e.g. FOLIO items carrying a `folio-item` source identifier);
    # `Identified` is the post-mint state; `Unidentifiable` is for items with no
    # stable source identifier.
    id: Identified | Unidentifiable | Identifiable
    title: str | None = None
    note: str | None = None
    locations: list[PhysicalLocation | DigitalLocation] = []
