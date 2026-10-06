"""Tests for the shared MARC field helpers in common.py."""

from __future__ import annotations

from pymarc.record import Field, Subfield
from structlog.testing import capture_logs

from adapters.transformers.marc.common import (
    non_repeatable_subfield,
    non_repeatable_subfields,
)


def _field(*subfields: tuple[str, str]) -> Field:
    return Field(tag="520", subfields=[Subfield(c, v) for c, v in subfields])


def test_single_value_is_stripped() -> None:
    assert non_repeatable_subfield(_field(("a", "  Cyntaf ")), "a") == "Cyntaf"


def test_missing_subfield_is_none() -> None:
    assert non_repeatable_subfield(_field(("b", "Ail")), "a") is None


def test_blank_subfield_is_none() -> None:
    assert non_repeatable_subfield(_field(("a", "   ")), "a") is None


def test_first_non_blank_value_is_used() -> None:
    field = _field(("a", "   "), ("a", "Ail"))
    with capture_logs() as logs:
        assert non_repeatable_subfield(field, "a") == "Ail"
    assert logs == []


def test_repeated_subfield_logs_and_takes_first() -> None:
    field = _field(("a", "Cyntaf"), ("a", "Ail"))
    with capture_logs() as logs:
        assert non_repeatable_subfield(field, "a") == "Cyntaf"
    assert len(logs) == 1
    assert logs[0]["log_level"] == "error"
    assert logs[0]["tag"] == "520"
    assert logs[0]["subfield"] == "a"


def test_subfields_keep_order_of_first_appearance() -> None:
    field = _field(("b", "Ail"), ("a", "Cyntaf"), ("c", "Trydydd"))
    assert non_repeatable_subfields(field, "a", "b", "c") == [
        "Ail",
        "Cyntaf",
        "Trydydd",
    ]


def test_subfields_ignores_other_codes_and_blanks() -> None:
    field = _field(("a", "Cyntaf"), ("u", "http://example.com"), ("b", "  "))
    assert non_repeatable_subfields(field, "a", "b") == ["Cyntaf"]


def test_subfields_reports_each_repeat_once() -> None:
    field = _field(("a", "Cyntaf"), ("a", "Ail"), ("b", "Un"), ("b", "Dau"))
    with capture_logs() as logs:
        assert non_repeatable_subfields(field, "a", "b") == ["Cyntaf", "Un"]
    assert [log["subfield"] for log in logs] == ["a", "b"]
