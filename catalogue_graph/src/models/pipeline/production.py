from models.pipeline.concept import Concept, Period
from models.pipeline.serialisable import SerialisableModel


class ProductionEvent(SerialisableModel):
    label: str
    places: list[Concept]
    agents: list[Concept]
    dates: list[Period]
    function: Concept | None = None
