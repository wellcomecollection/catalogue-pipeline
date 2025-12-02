from datetime import UTC, datetime


def ensure_datetime_utc(dt: datetime) -> datetime:
    """Ensure a datetime object is in UTC timezone."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def convert_datetime_to_utc_iso(dt: datetime) -> str:
    """Convert a datetime object to an ISO 8601 string in UTC timezone."""
    # strip +00:00 and replace with Z
    return dt.astimezone(UTC).isoformat().replace("+00:00", "Z")
