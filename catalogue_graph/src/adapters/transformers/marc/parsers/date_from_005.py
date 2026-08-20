from datetime import UTC, datetime


def datetime_from_005(value_005: str) -> datetime:
    """
    Converts a MARC 005 field string in 'YYYYMMDDHHMMSS.f' format to a datetime.

    MARC 005 values are defined as UTC, so the returned datetime is timezone-aware.
    Returning a naive datetime would make downstream conversions (e.g.
    `convert_datetime_to_utc_iso`, which calls `astimezone`) interpret the value as
    system-local time and shift it by the local UTC offset.

    Args:
        value_005 (str): The MARC 005 field string.

    Returns:
        datetime: The parsed UTC datetime from the MARC 005 field string.


    >>> datetime_from_005("20251225123045.0")
    datetime.datetime(2025, 12, 25, 12, 30, 45, tzinfo=datetime.timezone.utc)
    """
    return datetime.strptime(value_005, "%Y%m%d%H%M%S.%f").replace(tzinfo=UTC)
