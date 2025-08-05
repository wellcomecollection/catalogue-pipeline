from ingestor.models.display.access_method import get_display_access_method
from ingestor.models.display.access_status import get_display_access_status
from ingestor.models.indexable_work import DisplayAccessCondition


def get_display_access_condition(raw_condition: dict) -> DisplayAccessCondition:
    status = None
    if "status" in raw_condition:
        status = get_display_access_status(raw_condition["status"]["type"])

    return DisplayAccessCondition(
        method=get_display_access_method(raw_condition["method"]["type"]),
        status=status,
        terms=raw_condition.get("terms"),
        note=raw_condition.get("note"),
    )
