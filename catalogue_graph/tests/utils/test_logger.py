import io
import json
import logging
import sys
from collections.abc import Iterator
from typing import NoReturn

import pytest
import structlog

from utils.logger import setup_structlog


@pytest.fixture
def restore_logging_config() -> Iterator[None]:
    saved = structlog.get_config()
    yield
    structlog.configure(**saved)


@pytest.mark.usefixtures("restore_logging_config")
def test_an_exception_is_rendered_inside_one_json_line(
    caplog: pytest.LogCaptureFixture, monkeypatch: pytest.MonkeyPatch
) -> None:
    # A non-terminal stderr selects the JSON renderer, as in Lambda and ECS.
    monkeypatch.setattr(sys, "stderr", io.StringIO())
    setup_structlog()

    def fail(patron_barcode: str) -> NoReturn:
        raise KeyError("boom")

    # Kept off the lines the traceback quotes, so only rendered locals show it.
    barcode = "do-not-log-me"
    try:
        fail(patron_barcode=barcode)
    except KeyError:
        structlog.get_logger("test").exception("Step failed", window="w1")

    [record] = [r for r in caplog.records if r.name == "test"]
    assert record.exc_info is None
    line = logging.Formatter("%(message)s").format(record)
    assert "do-not-log-me" not in line
    assert "\n" not in line
    parsed = json.loads(line)
    assert parsed["event"] == "Step failed"
    assert parsed["window"] == "w1"
    assert "KeyError: 'boom'" in parsed["exception"]
    assert "exc_info" not in parsed
