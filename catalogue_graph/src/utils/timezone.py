from datetime import UTC, datetime


def convert_datetime_to_utc_iso(dt: datetime) -> str:
    """Convert a datetime object to an ISO 8601 string in UTC timezone."""
    # strip +00:00 and replace with Z
    return dt.astimezone(UTC).isoformat().replace("+00:00", "Z")
