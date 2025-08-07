from pydantic import BaseModel

from .concept import DisplayConcept


class DisplayProductionEvent(BaseModel):
    label: str
    places: list[DisplayConcept]
    agents: list[DisplayConcept]
    dates: list[DisplayConcept]
    function: DisplayConcept | None
    type: str = "ProductionEvent"
