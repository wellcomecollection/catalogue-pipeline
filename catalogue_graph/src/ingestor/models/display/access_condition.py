from collections.abc import Generator

from pydantic import BaseModel

from models.pipeline.location import Location

from .access_method import DisplayAccessMethod
from .access_status import DisplayAccessStatus
from .id_label import DisplayIdLabel


class DisplayAccessCondition(BaseModel):
    method: DisplayIdLabel
    status: DisplayIdLabel | None
    terms: str | None
    note: str | None
    type: str = "AccessCondition"

    @staticmethod
    def from_location(location: Location) -> Generator["DisplayAccessCondition"]:
        for condition in location.access_conditions:
            yield DisplayAccessCondition(
                method=DisplayAccessMethod.from_access_condition(condition),
                status=DisplayAccessStatus.from_access_condition(condition),
                terms=condition.terms,
                note=condition.note,
            )
