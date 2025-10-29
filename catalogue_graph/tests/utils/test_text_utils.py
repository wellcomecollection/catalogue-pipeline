from adapters.ebsco.transformers.text_utils import (
    normalise_label,
    trim_trailing,
    trim_trailing_period,
)


def test_trim_trailing_period_single() -> None:
    assert trim_trailing_period("Title.") == "Title"


def test_trim_trailing_period_double_kept() -> None:
    # Matches Scala regex behaviour: second-to-last char is a period so no trim
    assert trim_trailing_period("Title..") == "Title.."


def test_trim_trailing_period_ellipsis() -> None:
    # Ellipsis preserved
    assert trim_trailing_period("Title...") == "Title..."


def test_trim_trailing_comma() -> None:
    assert trim_trailing("Name, ", ",") == "Name"


def test_trim_trailing_colon() -> None:
    assert trim_trailing("Place:  ", ":") == "Place"


def test_normalise_label_concept_period() -> None:
    # Should remove a single trailing period
    assert normalise_label("History.", "Concept") == "History"


def test_normalise_label_agent_comma() -> None:
    assert normalise_label("Smith, ", "Person") == "Smith"


def test_normalise_label_place_colon() -> None:
    assert normalise_label("London: ", "Place") == "London"


def test_normalise_label_genre_electronic_books() -> None:
    assert normalise_label("Electronic Books", "Genre") == "Electronic books"


def test_normalise_label_subject_ellipsis_preserved() -> None:
    # Should keep ellipsis
    assert normalise_label("Something...", "Subject") == "Something..."
