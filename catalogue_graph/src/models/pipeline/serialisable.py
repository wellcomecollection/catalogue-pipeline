from datetime import UTC, datetime

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


def convert_datetime_to_iso_8601_with_z_suffix(dt: datetime) -> str:
    """
    Return an ISO-8601 UTC string with a 'Z' suffix.
    Behaviours:
      - If datetime is timezone-aware, convert to UTC before serialising.
      - Naive datetimes are assumed to already represent UTC.
      - Include fractional seconds only if microseconds are non-zero.
      - Trim trailing zeros from fractional seconds (e.g. 123000 -> 123; 120000 -> 12).
    Examples:
      2025-10-23T12:34:56Z
      2025-10-23T12:34:56.123Z
      2025-10-23T12:34:56.123456Z
    """
    if dt.tzinfo is not None:
        dt = dt.astimezone(UTC).replace(tzinfo=None)

    base = dt.strftime("%Y-%m-%dT%H:%M:%S")

    if dt.microsecond:
        micro = f"{dt.microsecond:06d}".rstrip("0")
        if micro:  # After trimming zeros could still be empty
            return f"{base}.{micro}Z"

    return f"{base}Z"


class ElasticsearchModel(BaseModel):
    # This model config automatically converts between snake_case and camelCase when validating and serialising.
    # Instances of this model (and its subclasses) can be constructed using both snake_case and camelCase properties,
    # allowing us to continue using snake_case in Python code and camelCase in Elasticsearch documents.
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
        serialize_by_alias=True,
        json_encoders={datetime: convert_datetime_to_iso_8601_with_z_suffix},
    )


class SerialisableModel(ElasticsearchModel):
    pass
