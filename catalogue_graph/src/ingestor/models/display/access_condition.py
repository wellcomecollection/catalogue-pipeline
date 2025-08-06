from pydantic import BaseModel

from ingestor.models.denormalised.work import Location

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
    def from_location(location: Location) -> "DisplayAccessCondition":
        for condition in location.accessConditions:
            yield DisplayAccessCondition(
                method=DisplayAccessMethod.from_access_condition(condition),
                status=DisplayAccessStatus.from_access_condition(condition),
                terms=condition.terms,
                note=condition.note,
            )
