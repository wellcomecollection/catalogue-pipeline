from collections import OrderedDict
from datetime import UTC, datetime
from typing import Any

import pytest
from pydantic import ValidationError

from adapters.utils.window_summary import WindowKey, WindowSummary

# Shared test data
BASE_DT = datetime(2025, 12, 1, 10, 30, 45, tzinfo=UTC)


def make_summary(**overrides: Any) -> WindowSummary:
    """Create a WindowSummary with sensible defaults."""
    defaults: dict[str, Any] = {
        "window_start": BASE_DT,
        "window_end": BASE_DT,
        "state": "success",
        "attempts": 1,
        "record_ids": [],
        "last_error": None,
        "updated_at": BASE_DT,
        "tags": None,
    }
    defaults.update(overrides)
    return WindowSummary(**defaults)


@pytest.mark.parametrize(
    "input_value,expected_output",
    [
        (BASE_DT, BASE_DT),  # datetime object
        ("2025-12-01T10:30:45+00:00", BASE_DT),  # ISO format string
    ],
    ids=["datetime_object", "iso_format_string"],
)
def test_datetime_coercion_accepts(
    input_value: object, expected_output: datetime
) -> None:
    """Test valid datetime inputs."""
    summary = make_summary(window_start=input_value)
    assert summary.window_start == expected_output


@pytest.mark.parametrize(
    "invalid_value",
    [123456789, "not-a-date", [2025, 12, 1]],
    ids=["unix_timestamp_int", "invalid_iso_string", "list_value"],
)
def test_datetime_coercion_rejects(invalid_value: object) -> None:
    """Test invalid datetime inputs."""
    with pytest.raises((ValidationError, TypeError)):
        make_summary(window_start=invalid_value)


@pytest.mark.parametrize(
    "input_value,expected_output",
    [
        (["id1", "id2", "id3"], ["id1", "id2", "id3"]),  # list of strings
        ([1, 2, 3], ["1", "2", "3"]),  # list of non-strings
        (("id1", "id2"), ["id1", "id2"]),  # tuple
        (None, []),  # None
        ("single_id", ["single_id"]),  # single value
        ([], []),  # empty list
    ],
    ids=["list_strings", "list_ints", "tuple", "none", "single_value", "empty_list"],
)
def test_record_ids_coercion(input_value: object, expected_output: list[str]) -> None:
    """Test record_ids field coercion."""
    summary = make_summary(record_ids=input_value)
    assert summary.record_ids == expected_output


@pytest.mark.parametrize(
    "input_value,expected_output",
    [
        (None, None),  # None
        ("Connection timeout", "Connection timeout"),  # string
        (404, "404"),  # integer
        (ValueError("Invalid config"), "Invalid config"),  # exception
    ],
    ids=["none", "string", "integer", "exception"],
)
def test_last_error_coercion(input_value: object, expected_output: str | None) -> None:
    """Test last_error field coercion."""
    summary = make_summary(last_error=input_value)
    assert summary.last_error == expected_output


@pytest.mark.parametrize(
    "input_value,expected_output",
    [
        (None, None),  # None
        (
            {"environment": "production", "version": "1.0"},
            {"environment": "production", "version": "1.0"},
        ),  # dict
        (
            {1: 2, "key": 3, 4: "value"},
            {"1": "2", "key": "3", "4": "value"},
        ),  # mixed types
        (
            OrderedDict([("first", "value1"), ("second", "value2")]),
            {"first": "value1", "second": "value2"},
        ),  # OrderedDict
        (
            [("key1", "value1"), ("key2", "value2")],
            {"key1": "value1", "key2": "value2"},
        ),  # list of tuples
        ({}, {}),  # empty dict
    ],
    ids=[
        "none",
        "dict_strings",
        "dict_mixed_types",
        "ordered_dict",
        "list_tuples",
        "empty_dict",
    ],
)
def test_tags_coercion_accepts(
    input_value: object, expected_output: dict[str, str] | None
) -> None:
    """Test valid tags inputs."""
    summary = make_summary(tags=input_value)
    assert summary.tags == expected_output


@pytest.mark.parametrize(
    "invalid_value,expected_type_name",
    [
        ("some string", "str"),
        (42, "int"),
        (["key1", "key2"], "list"),
        ({"key1", "key2"}, "set"),
        (True, "bool"),
        (3.14, "float"),
    ],
    ids=["string", "integer", "plain_list", "set", "boolean", "float"],
)
def test_tags_coercion_rejects(invalid_value: object, expected_type_name: str) -> None:
    """Test invalid tags inputs with proper error messages."""
    with pytest.raises(ValidationError) as exc_info:
        make_summary(tags=invalid_value)
    error_str = str(exc_info.value)
    assert "tags must be a dict or dict-like object" in error_str
    assert expected_type_name in error_str


def test_window_key_from_dates() -> None:
    """Test that window_key is derived from window_start and window_end."""
    start = datetime(2025, 12, 1, 10, 0, tzinfo=UTC)
    end = datetime(2025, 12, 1, 12, 0, tzinfo=UTC)
    summary = make_summary(window_start=start, window_end=end)
    expected_key = f"{start.isoformat()}_{end.isoformat()}"
    assert summary.window_key == expected_key


def test_window_key_can_be_parsed_back() -> None:
    """Test that window_key can be parsed back to dates."""
    start = datetime(2025, 12, 1, 10, 0, tzinfo=UTC)
    end = datetime(2025, 12, 1, 12, 0, tzinfo=UTC)
    summary = make_summary(window_start=start, window_end=end)
    parsed_start, parsed_end = WindowKey.parse(summary.window_key)
    assert parsed_start == start
    assert parsed_end == end


def test_mixed_coercion_types() -> None:
    """Test creating WindowSummary with mixed input types."""
    summary = make_summary(
        window_start="2025-12-01T10:00:00+00:00",
        record_ids=[1, "id2", 3],
        updated_at="2025-12-01T13:00:00+00:00",
        tags={"env": "prod", 1: 2},
    )
    assert isinstance(summary.window_start, datetime)
    assert isinstance(summary.updated_at, datetime)
    assert summary.record_ids == ["1", "id2", "3"]
    assert summary.tags == {"env": "prod", "1": "2"}


def test_all_none_optional_fields() -> None:
    """Test creating WindowSummary with all optional fields as None."""
    summary = make_summary(
        state="pending",
        attempts=0,
        record_ids=None,
        last_error=None,
        tags=None,
    )
    assert summary.record_ids == []
    assert summary.last_error is None
    assert summary.tags is None
