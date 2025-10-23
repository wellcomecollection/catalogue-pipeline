from datetime import datetime, timedelta, timezone

# src is added to PYTHONPATH via pytest config, so we import from package root under src
from models.pipeline.serialisable import SerialisableModel


class SampleModel(SerialisableModel):
    created_at: datetime
    some_value: int


def _dump(model: SampleModel) -> str:
    return model.model_dump_json()


def test_datetime_without_microseconds_serialises_without_fraction() -> None:
    dt = datetime(2025, 10, 23, 12, 34, 56)
    m = SampleModel(created_at=dt, some_value=1)
    assert '"createdAt":"2025-10-23T12:34:56Z"' in _dump(m)


def test_datetime_with_full_microseconds_serialises_full_fraction() -> None:
    dt = datetime(2025, 10, 23, 12, 34, 56, 123456)
    m = SampleModel(created_at=dt, some_value=1)
    assert '"createdAt":"2025-10-23T12:34:56.123456Z"' in _dump(m)


def test_datetime_with_trailing_zero_microseconds_trims_fraction() -> None:
    dt = datetime(2025, 10, 23, 12, 34, 56, 123000)
    m = SampleModel(created_at=dt, some_value=1)
    assert '"createdAt":"2025-10-23T12:34:56.123Z"' in _dump(m)


def test_datetime_with_many_trailing_zeros_trims_all() -> None:
    dt = datetime(2025, 10, 23, 12, 34, 56, 120000)  # 120000 -> "12"
    m = SampleModel(created_at=dt, some_value=1)
    assert '"createdAt":"2025-10-23T12:34:56.12Z"' in _dump(m)


def test_timezone_aware_datetime_converted_to_utc() -> None:
    # 13:34:56+01:00 should become 12:34:56Z
    tz_dt = datetime(
        2025, 10, 23, 13, 34, 56, 654321, tzinfo=timezone(timedelta(hours=1))
    )
    m = SampleModel(created_at=tz_dt, some_value=1)
    assert '"createdAt":"2025-10-23T12:34:56.654321Z"' in _dump(m)


def test_alias_inbound_and_outbound_serialisation() -> None:
    # Provide snake_case inbound, ensure outbound serialises camelCase keys
    dt = datetime(2025, 10, 23, 12, 0, 0)
    m = SampleModel(created_at=dt, some_value=42)
    dumped = _dump(m)
    assert '"createdAt":"2025-10-23T12:00:00Z"' in dumped
    assert '"someValue":42' in dumped
    # model fields accessible via snake_case
    assert m.created_at == dt
    assert m.some_value == 42
