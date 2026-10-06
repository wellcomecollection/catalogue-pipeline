from datetime import date

import pytest

from adapters.transformers.marc.parsers.field008 import Field008, Field008Dates
from adapters.transformers.marc.parsers.period import MAX


def years(label: str, first: int, last: int | None) -> Field008Dates:
    return Field008Dates(
        label, (date(first, 1, 1), MAX if last is None else date(last, 12, 31))
    )


# The inputs of Marc008DateParserTest.scala, with its expected values.
@pytest.mark.parametrize(
    "dates, expected",
    [
        ("s1757    ", years("1757", 1757, 1757)),
        ("s19uu    ", years("1900-1999", 1900, 1999)),
        ("s199u    ", years("1990-1999", 1990, 1999)),
        ("m16251700", years("1625-1700", 1625, 1700)),
        ("r19961925", years("1996", 1996, 1996)),
        (
            "e19081121",
            Field008Dates("1908/11/21", (date(1908, 11, 21), date(1908, 11, 21))),
        ),
        ("t19071907", years("1907", 1907, 1907)),
        ("d19161924", years("1916-1924", 1916, 1924)),
        ("c20009999", years("2000-", 2000, None)),
        ("u1959uuuu", years("1959-", 1959, None)),
        ("q16001699", years("1600-1699", 1600, 1699)),
        ("p20082006", years("2008", 2008, 2008)),
        ("s1874####", years("1874", 1874, 1874)),
        ("c20179999", years("2017-", 2017, None)),
    ],
)
def test_dates(dates: str, expected: Field008Dates) -> None:
    assert Field008(f"750101{dates}xxu").dates == expected


# The inputs of MarcProductionEventParserTest.scala.
@pytest.mark.parametrize(
    "field, expected, place",
    [
        (
            "790922s1757    enk||||      o00||||eng ccam   ",
            years("1757", 1757, 1757),
            "England",
        ),
        (
            "      s2003    enk050        0   vneng dngm a ",
            years("2003", 2003, 2003),
            "England",
        ),
        (
            "030623e19081121ua                k0eng dnkm a ",
            Field008Dates("1908/11/21", (date(1908, 11, 21), date(1908, 11, 21))),
            "Egypt",
        ),
        (
            "030818q16001699                  kn    dnka a ",
            years("1600-1699", 1600, 1699),
            None,
        ),
        (
            "090914uuuuuuuuuxx                  engddnteuua",
            None,
            "No place, unknown, or undetermined",
        ),
    ],
)
def test_whole_field(
    field: str, expected: Field008Dates | None, place: str | None
) -> None:
    field008 = Field008(field)
    assert field008.dates == expected
    assert field008.place_of_production == place


@pytest.mark.parametrize(
    "dates",
    [
        "e19750231",
        "e191607uu",
        "suuuu    ",
        "s        ",
        "s196?    ",
        "s0uuu    ",
        "s1uuu    ",
        "s19u5    ",
        "n1979uuuu",
        "|1979uuuu",
        " 1979    ",
        "i19751980",
        "k19751980",
        "b        ",
        "x1975    ",
    ],
)
def test_no_dates(dates: str) -> None:
    assert Field008(f"750101{dates}xxu").dates is None


def test_short_field() -> None:
    assert Field008("750101").dates is None


# Deliberate divergences: the Scala parser gives no date for any of these,
# for the reason given on each. The Python parser reads any "u" digit and
# ignores date 2 wherever it does not need it.
@pytest.mark.parametrize(
    "dates, expected",
    [
        # s needs a blank date 2
        ("s192u2009", years("1920-1929", 1920, 1929)),
        # r and t need a year, century or decade in date 2
        ("r192u2009", years("1920-1929", 1920, 1929)),
        ("t1985uuuu", years("1985", 1985, 1985)),
        # p, d, m and q need two full years
        ("p192u1980", years("1920-1929", 1920, 1929)),
        ("d19uu200u", years("1900-2009", 1900, 2009)),
        ("m191u195u", years("1910-1959", 1910, 1959)),
        ("q19uu    ", years("1900-", 1900, None)),
        ("d1975uuuu", years("1975-", 1975, None)),
        ("m1975    ", years("1975-", 1975, None)),
        # c and u need a full year in date 1 and "9999" or "uuuu" in date 2
        ("c1979uuuu", years("1979-", 1979, None)),
        ("u19uu    ", years("1900-", 1900, None)),
    ],
)
def test_dates_the_scala_parser_rejects(dates: str, expected: Field008Dates) -> None:
    assert Field008(f"750101{dates}xxu").dates == expected
