from __future__ import annotations

import re
from collections.abc import Generator

import pytest
from pytest_bdd import parsers, then
from structlog.testing import capture_logs
from structlog.typing import EventDict


@pytest.fixture(autouse=True)
def captured_logs() -> Generator[list[EventDict], None, None]:
    """Collect structlog events emitted while transforming a record; caplog does not see them."""
    with capture_logs() as entries:
        yield entries


def _errors_logged(captured_logs: list[EventDict], message: str) -> list[EventDict]:
    return [
        entry
        for entry in captured_logs
        if entry.get("log_level") == "error" and entry.get("event") == message
    ]


def _has_values(entry: EventDict, expected: list[tuple[str, str]]) -> bool:
    return all(key in entry and str(entry[key]) == value for key, value in expected)


def _captured_report(captured_logs: list[EventDict]) -> str:
    if not captured_logs:
        return "No events were logged."
    return "Logged events were:\n" + "\n".join(str(entry) for entry in captured_logs)


@then(parsers.parse('an error "{message}" is logged'))
def step_error_logged(captured_logs: list[EventDict], message: str) -> None:
    assert _errors_logged(captured_logs, message), (
        f'Expected an error logged with message: "{message}". '
        + _captured_report(captured_logs)
    )


@then(parsers.re(r'an error "(?P<message>[^"]*)" is logged with (?P<pairs>.+)'))
def step_error_logged_with(
    captured_logs: list[EventDict], message: str, pairs: str
) -> None:
    """`pairs` is one or more `key "value"` terms joined by `and`."""
    expected = re.findall(r'(\w+) "([^"]*)"', pairs)
    assert expected, f'No key "value" pairs found in: {pairs}'
    matches = _errors_logged(captured_logs, message)
    assert any(_has_values(entry, expected) for entry in matches), (
        f'Expected an error logged with message: "{message}" and {pairs}. '
        + _captured_report(captured_logs)
    )
