from pydantic import BaseModel

from models.pipeline.concept import Concept, Period


class ProductionEvent(BaseModel):
    label: str
    places: list[Concept]
    agents: list[Concept]
    dates: list[Period]
    function: Concept | None = None
