from __future__ import annotations

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


@then(parsers.parse('an error "{message}" is logged with {key} "{value}"'))
def step_error_logged_with(
    captured_logs: list[EventDict], message: str, key: str, value: str
) -> None:
    matches = _errors_logged(captured_logs, message)
    assert any(str(entry.get(key)) == value for entry in matches), (
        f'Expected an error logged with message: "{message}" and {key}="{value}". '
        + _captured_report(captured_logs)
    )
