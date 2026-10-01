from datetime import date

import pytest

from adapters.transformers.ebsco.parsers.field008 import Field008, Field008Dates
from adapters.transformers.marc.parsers.period import MAX


def years(label: str, first: int, last: int | None) -> Field008Dates:
    return Field008Dates(
        label, (date(first, 1, 1), MAX if last is None else date(last, 12, 31))
    )


@pytest.mark.parametrize(
    "dates, expected",
    [
        ("s1925    ", years("1925", 1925, 1925)),
        ("s192u    ", years("1920-1929", 1920, 1929)),
        ("s19uu    ", years("1900-1999", 1900, 1999)),
        ("s20uu    ", years("2000-2099", 2000, 2099)),
        ("r192u2009", years("1920-1929", 1920, 1929)),
        ("t19851983", years("1985", 1985, 1985)),
        ("p19751980", years("1975", 1975, 1975)),
        ("c19799999", years("1979-", 1979, None)),
        ("u1979uuuu", years("1979-", 1979, None)),
        ("u19uuuuuu", years("1900-", 1900, None)),
        ("d19252009", years("1925-2009", 1925, 2009)),
        ("m19011956", years("1901-1956", 1901, 1956)),
        ("q19251956", years("1925-1956", 1925, 1956)),
        ("m19751975", years("1975-1975", 1975, 1975)),
        ("d19uu200u", years("1900-2009", 1900, 2009)),
        ("d19759999", years("1975-", 1975, None)),
        ("m19759999", years("1975-", 1975, None)),
        # an unknown end is open; the Scala pipeline gives no date here
        ("d1975uuuu", years("1975-", 1975, None)),
        ("m1975    ", years("1975-", 1975, None)),
        (
            "e19750501",
            Field008Dates("1975/05/01", (date(1975, 5, 1), date(1975, 5, 1))),
        ),
        ("e19750231", None),
        ("e191607uu", None),
        ("suuuu    ", None),
        ("s        ", None),
        ("s196?    ", None),
        ("s0uuu    ", None),
        ("n1979uuuu", None),
        ("|1979uuuu", None),
        (" 1979    ", None),
        ("i19751980", None),
        ("k19751980", None),
        ("b        ", None),
        ("x1975    ", None),
    ],
)
def test_dates(dates: str, expected: Field008Dates | None) -> None:
    assert Field008(f"750101{dates}xxu").dates == expected
