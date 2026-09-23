"""The range helpers and the fallback on their own. They take normalised text."""

from datetime import date

import pytest

from adapters.transformers.marc.parsers.period import (
    MAX,
    MIN,
    Span,
    borrow_from_right,
    closed_range,
    complete_short_year,
    fallback,
    open_range,
)


def years(start: int, end: int) -> Span:
    return date(start, 1, 1), date(end, 12, 31)


@pytest.mark.parametrize(
    "text, expected",
    [
        ("to 1500", (MIN, date(1500, 12, 31))),
        ("early works to 1800", (MIN, date(1800, 12, 31))),
        ("before 1800", (MIN, date(1800, 12, 31))),
        ("-1953", (MIN, date(1953, 12, 31))),
        ("to 168", (MIN, date(168, 12, 31))),
        ("to 1500s", (MIN, date(1509, 12, 31))),
        ("to 19th century", (MIN, date(1899, 12, 31))),
        ("to nov 2007", (MIN, date(2007, 11, 30))),
        ("after 1817", (date(1817, 1, 1), MAX)),
        ("1994-", (date(1994, 1, 1), MAX)),
        ("1970s-", (date(1970, 1, 1), MAX)),
        ("nov 2007-", (date(2007, 11, 1), MAX)),
        (
            "~1994-",
            (date(1984, 1, 1), MAX),
        ),  # a circa start is widened, the end stays open
        ("pre 1900", years(1890, 1900)),
        ("post-1965", years(1965, 1974)),
        ("pre 1900s", years(1890, 1909)),
        ("post 19th century", years(1800, 1908)),
        ("pre nov 2007", years(1997, 2007)),  # widening works in whole years
        ("to 12", None),
        ("to", None),
        ("before", None),
        ("after", None),
        ("-", None),
        ("-1994-", None),
        ("1994-1995", None),  # closed, for closed_range
        ("1984", None),
    ],
)
def test_open_range(text: str, expected: Span | None) -> None:
    assert open_range(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("1988-1990", years(1988, 1990)),
        ("1897-99", years(1897, 1899)),
        ("1750-1", years(1750, 1751)),
        ("between 1870 and 1879", years(1870, 1879)),
        ("1500 to 1600", years(1500, 1600)),
        ("1711/12", years(1711, 1712)),
        ("1890 /1910", years(1890, 1910)),
        ("1990s-2000s", years(1990, 2009)),
        ("19th-20th centuries", years(1800, 1999)),
        ("late 19th-early 20th century", years(1860, 1939)),
        ("may-june 1960", (date(1960, 5, 1), date(1960, 6, 30))),
        ("nov 2007-dec 2007", (date(2007, 11, 1), date(2007, 12, 31))),
        ("12-19 january 1990", (date(1990, 1, 12), date(1990, 1, 19))),
        ("1933 july 1.-1933 july 31.", (date(1933, 7, 1), date(1933, 7, 31))),
        ("~1955-1984", years(1945, 1984)),
        ("~1955-~1984", years(1945, 1993)),
        ("mid-1970s", years(1973, 1976)),  # one expression split by its own hyphen
        ("mid-late 1960s", years(1963, 1969)),
        ("1994/1995-1996", years(1994, 1996)),
        ("1657-1562", years(1657, 1562)),  # backwards; `parse` drops it
        ("1984", None),
        ("1994-", None),
        ("-1953", None),
        ("to 1500", None),
        ("ancient-modern", None),
        ("1 and 2", None),
    ],
)
def test_closed_range(text: str, expected: Span | None) -> None:
    assert closed_range(text) == expected


@pytest.mark.parametrize(
    "left, right, expected",
    [
        ("1897", "99", "1899"),
        ("1750", "1", "1751"),
        ("1750", "51", "1751"),
        ("2004", "9", "2009"),
        ("may 1960", "9", "1969"),
        ("mid 1970s", "9", "1979"),
        ("1897", "1899", "1899"),
        ("1897", "099", "099"),
        ("1897", "june 1899", "june 1899"),
        ("abc", "99", "99"),  # no year on the left to borrow from
    ],
)
def test_complete_short_year(left: str, right: str, expected: str) -> None:
    assert complete_short_year(left, right) == expected


@pytest.mark.parametrize(
    "left, right, expected",
    [
        ("may", "june 1960", "may 1960"),
        ("nov", "2007", "nov 2007"),
        ("12", "19 january 1990", "12 january 1990"),
        ("19th", "20th century", "19th century"),
        ("late 19th", "early 20th century", "late 19th century"),
        ("mid", "1970s", "mid 1970s"),
        ("abc", "def 1990", "abc 1990"),
        ("1897", "1899", "1897"),  # a year of its own: nothing borrowed
        ("~1955", "1984", "~1955"),
        ("early 1970s", "1980s", "early 1970s"),  # already a date: nothing borrowed
        ("19th century", "late 20th century", "19th century"),
    ],
)
def test_borrow_from_right(left: str, right: str, expected: str) -> None:
    assert borrow_from_right(left, right) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("1984", years(1984, 1984)),
        ("printed in 1984", years(1984, 1984)),
        ("1984 and 1990", years(1984, 1990)),
        ("1990 1984", years(1984, 1990)),
        ("1984-1990", years(1984, 1990)),
        ("1984a", years(1984, 1984)),
        ("1984+", years(1984, 1984)),
        ("coup d u+2019 état 1797", years(1797, 1797)),
        ("+1984", None),
        ("12345", None),
        ("2041", None),
        ("0000", None),
        ("19th century", None),
        ("abc", None),
        ("", None),
    ],
)
def test_fallback(text: str, expected: Span | None) -> None:
    assert fallback(text) == expected
