"""Each atom on its own. Atoms take normalised text: lower case, no brackets, no trailing dot."""

from datetime import date

import pytest

from adapters.transformers.marc.parsers.period import (
    Span,
    atom,
    century,
    circa,
    day,
    decade,
    end_of_month,
    month,
    season,
    widen,
    year,
)


def years(start: int, end: int) -> Span:
    return date(start, 1, 1), date(end, 12, 31)


@pytest.mark.parametrize(
    "text, expected",
    [
        ("1984", years(1984, 1984)),
        ("476", years(476, 476)),
        ("0476", years(476, 476)),
        ("2040", years(2040, 2040)),  # LATEST_YEAR
        ("19", None),
        ("12345", None),
        ("1984.", None),
        ("1984 ", None),
        ("abcd", None),
    ],
)
def test_year(text: str, expected: Span | None) -> None:
    assert year(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("1970s", years(1970, 1979)),
        ("early 1970s", years(1970, 1973)),
        ("mid 1970s", years(1973, 1976)),
        ("middle 1970s", years(1973, 1976)),
        ("late 1970s", years(1976, 1979)),
        ("0500s", years(500, 509)),
        ("1970s.", None),
        ("1970's", None),
        ("early-1970s", years(1970, 1973)),
        ("early to mid 1970s", years(1970, 1976)),
        ("mid-to-late 1970s", years(1973, 1979)),
        ("early mid 1970s", years(1970, 1976)),
        ("early to mid-1970s", years(1970, 1976)),
        ("197s", None),
    ],
)
def test_decade(text: str, expected: Span | None) -> None:
    assert decade(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("19th century", years(1800, 1899)),
        ("19th cent", years(1800, 1899)),
        ("19 cent.", years(1800, 1899)),
        ("1st century", years(1, 99)),
        ("2nd century", years(100, 199)),
        ("3rd century", years(200, 299)),
        ("21st century", years(2000, 2099)),
        ("100th century", None),
        ("early-mid 20th century", years(1900, 1969)),
        ("mid 19th century", years(1830, 1869)),
        ("mid-19th century", years(1830, 1869)),
        ("middle 19th century", years(1830, 1869)),
        ("late 20th century", years(1960, 1999)),
        ("mid to late 19th century", years(1830, 1899)),
        ("mid-to-late 19th century", years(1830, 1899)),
        ("early to late 19th century", years(1800, 1899)),
        ("early to mid-20th century", years(1900, 1969)),
        ("late 19th-early 20th century", None),  # a range, for closed_range
        ("19th centuries", None),
        ("19th century.", None),
    ],
)
def test_century(text: str, expected: Span | None) -> None:
    assert century(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("spring 1996", (date(1996, 3, 1), date(1996, 5, 31))),
        ("summer 1997", (date(1997, 6, 1), date(1997, 8, 31))),
        ("autumn 1967", (date(1967, 9, 1), date(1967, 11, 30))),
        ("fall 1967", (date(1967, 9, 1), date(1967, 11, 30))),
        ("winter 1962", (date(1962, 12, 1), date(1963, 2, 28))),
        ("winter 1963", (date(1963, 12, 1), date(1964, 2, 29))),  # into a leap year
        ("winter 2040", (date(2040, 12, 1), date(2041, 2, 28))),
        ("spring 96", None),
        ("spring", None),
        ("spring-1996", None),
    ],
)
def test_season(text: str, expected: Span | None) -> None:
    assert season(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("nov 2007", (date(2007, 11, 1), date(2007, 11, 30))),
        ("nov. 2007", (date(2007, 11, 1), date(2007, 11, 30))),
        ("november 2007", (date(2007, 11, 1), date(2007, 11, 30))),
        ("sept 2007", (date(2007, 9, 1), date(2007, 9, 30))),
        ("may 1960", (date(1960, 5, 1), date(1960, 5, 31))),
        ("feb 2000", (date(2000, 2, 1), date(2000, 2, 29))),
        ("feb 1900", (date(1900, 2, 1), date(1900, 2, 28))),  # 1900 was not a leap year
        ("nov 07", None),
        ("novem 2007", None),
        ("2007 nov", (date(2007, 11, 1), date(2007, 11, 30))),
    ],
)
def test_month(text: str, expected: Span | None) -> None:
    assert month(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("14 nov 2007", (date(2007, 11, 14), date(2007, 11, 14))),
        ("14th nov. 2007", (date(2007, 11, 14), date(2007, 11, 14))),
        ("1st june 1813", (date(1813, 6, 1), date(1813, 6, 1))),
        ("november 14 2007", (date(2007, 11, 14), date(2007, 11, 14))),
        ("1851 nov 27", (date(1851, 11, 27), date(1851, 11, 27))),
        ("14/11/2007", (date(2007, 11, 14), date(2007, 11, 14))),
        ("14.11.2007", (date(2007, 11, 14), date(2007, 11, 14))),
        ("29 february 1976", (date(1976, 2, 29), date(1976, 2, 29))),
        ("14 nov 07", None),
        ("14-11-2007", None),
        ("2007-11-14", None),
    ],
)
def test_day(text: str, expected: Span | None) -> None:
    assert day(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("~1930", years(1920, 1939)),
        ("~ 1930", years(1920, 1939)),
        ("~1930s", years(1920, 1949)),
        ("~19th century", years(1790, 1909)),
        ("~nov 2007", (date(2007, 11, 1), date(2007, 11, 30))),
        ("~14 nov 2007", (date(2007, 11, 14), date(2007, 11, 14))),
        ("~winter 1962", (date(1962, 12, 1), date(1963, 2, 28))),
        ("1984", years(1984, 1984)),
        ("1984.", None),
        ("~abc", None),
        # implausible or impossible years, refused here rather than in the individual atoms
        ("11/14/2007", None),  # month first is not read
        ("spring 0000", None),
        ("spring 3000", None),
        ("nov 3000", None),
        ("31/04/1994", None),
        ("14 nov 0000", None),
        ("31/12/2500", None),
        ("0 nov 2007", None),
        ("32 nov 2007", None),
        ("0000", None),
        ("2041", None),
        ("0000s", None),
        ("6400s", None),
        ("0th century", None),
        ("22nd century", None),
        ("nov 0000", None),
        ("29 february 1975", None),
        ("~", None),
        ("", None),
    ],
)
def test_atom(text: str, expected: Span | None) -> None:
    assert atom(text) == expected


@pytest.mark.parametrize(
    "span, expected",
    [
        (None, None),
        (years(1930, 1930), years(1920, 1939)),
        (years(1970, 1979), years(1960, 1989)),
        (years(1800, 1899), years(1790, 1909)),
        (years(1990, 1995), years(1980, 2005)),
        (years(5, 5), years(1, 14)),
        (
            (date(2007, 11, 1), date(2007, 11, 30)),
            (date(2007, 11, 1), date(2007, 11, 30)),
        ),
        (
            (date(2007, 11, 14), date(2007, 11, 14)),
            (date(2007, 11, 14), date(2007, 11, 14)),
        ),
        (
            (date(1962, 12, 1), date(1963, 2, 28)),
            (date(1962, 12, 1), date(1963, 2, 28)),
        ),
    ],
)
def test_circa(span: Span | None, expected: Span | None) -> None:
    assert circa(span) == expected


@pytest.mark.parametrize(
    "span, before, after, expected",
    [
        (years(1930, 1930), -10, 9, years(1920, 1939)),
        ((date(1930, 3, 5), date(1930, 7, 9)), 0, 0, years(1930, 1930)),
        (years(5, 5), -10, 0, years(1, 5)),
        (years(9995, 9995), 0, 9, years(9995, 9999)),
    ],
)
def test_widen(span: Span, before: int, after: int, expected: Span) -> None:
    assert widen(span, before, after) == expected


@pytest.mark.parametrize(
    "year_, month_, expected",
    [(2000, 2, 29), (1900, 2, 28), (2007, 11, 30), (2007, 12, 31)],
)
def test_end_of_month(year_: int, month_: int, expected: int) -> None:
    assert end_of_month(year_, month_) == date(year_, month_, expected)
