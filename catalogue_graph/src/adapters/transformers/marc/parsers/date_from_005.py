from datetime import datetime


def datetime_from_005(value_005: str) -> datetime:
    """
    Converts a MARC 005 field string in 'YYYYMMDDHHMMSS.f' format to a datetime.

    Args:
        value_005 (str): The MARC 005 field string.

    Returns:
        datetime: The parsed datetime object from the MARC 005 field string.


    >>> datetime_from_005("20251225123045.0")
    datetime.datetime(2025, 12, 25, 12, 30, 45)
    """
    return datetime.strptime(value_005, "%Y%m%d%H%M%S.%f")
