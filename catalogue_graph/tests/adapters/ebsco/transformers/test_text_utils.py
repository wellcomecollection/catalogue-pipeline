import pytest

from adapters.ebsco.transformers.text_utils import (
    normalise_label,
)
from utils.types import ConceptType


def test_identifier_and_label_normalisation_are_separate() -> None:
    """Regression guard: label trimming does not collapse internal whitespace (identifier path handles that separately).

    With removal of normalise_identifier_value, identifier generation now uses identifier_from_text which preserves
    internal spacing except for leading/trailing trim and accent/period handling. This test simply asserts that
    label normalisation alone leaves internal spacing intact.
    """
    assert normalise_label("Mixed   Case  Label", "Concept") == "Mixed   Case  Label"


# Concept/Genre/Subject: trim single trailing period (not ellipsis)
@pytest.mark.parametrize(
    "label,concept_type,expected",
    [
        ("Example.", "Concept", "Example"),
        (
            "Example..",
            "Concept",
            "Example..",
        ),  # double period not ending with single char before period pattern
        ("Text ...", "Concept", "Text ..."),
        ("End...", "Concept", "End..."),  # ellipsis preserved
        ("Genre Title.", "Genre", "Genre Title"),
        ("Subject.", "Subject", "Subject"),
    ],
)
def test_period_trimming(label: str, concept_type: ConceptType, expected: str) -> None:
    assert normalise_label(label, concept_type) == expected


# Person/Organisation/Meeting: trim trailing comma
@pytest.mark.parametrize(
    "label,concept_type,expected",
    [
        ("Jane Doe,", "Person", "Jane Doe"),
        ("Acme Corp, ", "Organisation", "Acme Corp"),
        ("Annual Meeting,", "Meeting", "Annual Meeting"),
        (
            "Trailing comma, more",
            "Person",
            "Trailing comma, more",
        ),  # internal comma preserved
    ],
)
def test_comma_trimming(label: str, concept_type: ConceptType, expected: str) -> None:
    assert normalise_label(label, concept_type) == expected


# Place: trim trailing colon
@pytest.mark.parametrize(
    "label,expected",
    [
        ("London:", "London"),
        ("Paris: ", "Paris"),
        ("New York: Description", "New York: Description"),  # internal colon preserved
    ],
)
def test_place_colon_trimming(label: str, expected: str) -> None:
    assert normalise_label(label, "Place") == expected


# Period: unchanged (aside from strip)
@pytest.mark.parametrize(
    "label,expected",
    [
        ("1990-2000.", "1990-2000"),
        ("1950-1960, ", "1950-1960,"),
        ("Ancient Rome: ", "Ancient Rome:"),
    ],
)
def test_period_no_trimming(label: str, expected: str) -> None:
    assert normalise_label("  " + label + "  ", "Period") == expected


# Genre special case replacement
@pytest.mark.parametrize(
    "label,expected",
    [
        ("Electronic Books", "Electronic books"),
        ("Electronic Books.", "Electronic books"),
        ("Not Electronic Books", "Not Electronic Books"),
    ],
)
def test_genre_electronic_books_replacement(label: str, expected: str) -> None:
    assert normalise_label(label, "Genre") == expected
