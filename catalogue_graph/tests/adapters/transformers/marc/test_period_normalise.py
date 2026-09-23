"""Each normalisation step on its own. Spacing is only tidied at the end of `normalise`, so the
steps are compared with runs of spaces collapsed."""

import pytest

from adapters.transformers.marc.parsers.period import (
    Source,
    convert_roman_numerals,
    expand_placeholders,
    mark_circa,
    normalise,
    strip_noise,
    take_corrections,
)


def spaced(text: str) -> str:
    return " ".join(text.split())


@pytest.mark.parametrize(
    "text, expected",
    [
        ("m.dcc.xlv.", "1745."),
        ("anno m.d.xxxi.", "anno 1531."),
        ("mdcclxxv. [1775]", "1775. [1775]"),
        ("m.dcc.xlv.-m.dcc.l.", "1745.-1750."),
        ("m.d.xxviij [1528]", "1528 [1528]"),
        ("an viii", "an 8"),
        ("printed in the year, mdciii", "printed in the year, 1603"),
        ("civil war", "civil war"),
        ("mill 1900", "mill 1900"),
        ("mid 19th century", "mid 19th century"),
        ("c1977", "c1977"),
        ("vol. ii", "vol. 2"),
        ("m dcc xlix", "1749"),
        ("xvii1737", "xvii1737"),
        ("mdcclxi, mdcclxiii", "mdcclxi, mdcclxiii"),
        ("m.dcc.lxl", "m.dcc.lxl"),
        ("MDCC", "MDCC"),
    ],
)
def test_convert_roman_numerals(text: str, expected: str) -> None:
    assert convert_roman_numerals(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("1709 [1710]", "[1710]"),
        ("1709. [1710?]", "[1710]"),
        ("1798' [1748?]", "[1748]"),
        ("[1606] [1706]", "[[1706]"),
        ("5782 [i.e. 1782]", "1782]"),
        ("1733 [ie. 1744?]", "1744?]"),
        ("1844 [i.e 1878]", "1878]"),
        ("1556 [i.e].", "1556 [i.e]."),
        ("1700-[1703]", "1700-[1703]"),
        ("[1929]", "[1929]"),
        ("12 may 1750", "12 may 1750"),
        ("1709[1710]", "[1710]"),
        ("1709 [1710] [1711]", "[1710] [1711]"),
        ("1709 [1710] printed", "[1710] printed"),
        ("circa 1709 [1710]", "circa [1710]"),
        ("12 may 1750 [1751]", "12 may [1751]"),
        ("i.e. 1782", "1782"),
        ("1709 [1710-1711]", "1709 [1710-1711]"),
        ("1709 (1710)", "1709 (1710)"),
        ("1709 [10]", "1709 [10]"),
    ],
)
def test_take_corrections(text: str, expected: str) -> None:
    assert spaced(take_corrections(text)) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("199?", "1990s"),
        ("192-", "1920s"),
        ("[201 ]", "[2010s]"),
        ("[190 .]", "[1900s.]"),
        ("19--", "20th century"),
        ("19??", "20th century"),
        ("[19 ]", "[20th century]"),
        ("1875-[19--?]", "1875-[20th century?]"),
        ("1994-", "1994-"),
        ("476-1268", "476-1268"),
        ("1929", "1929"),
        ("192?-193?", "1920s-1930s"),
        ("200?", "2000s"),
        ("199-1999", "199-1999"),
        ("1990-", "1990-"),
        ("[2000 ]", "[2000 ]"),
        ("[18-]", "[18-]"),
        ("[1---]", "[1---]"),
    ],
)
def test_expand_placeholders(text: str, expected: str) -> None:
    assert expand_placeholders(text) == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        ("174[2]", "1742"),
        ("1[7]17", "1717"),
        ("[1929]", "1929"),
        ("(1929?)", "1929"),
        ("©1981", "1981"),
        ("revolution, 1775-1783", "revolution 1775-1783"),
        ("march 8, 1800", "march 8 1800"),
        ("1719, 1720", "1719, 1720"),
        ("sep-1965", "sep 1965"),
        ("may-june 1960", "may-june 1960"),
        ("[[1706]", "1706"),
        ("1[841-1849]", "1841-1849"),
        ("©1928, ©1929-1936", "1928, 1929-1936"),
        ("sept.-1965", "sept 1965"),
        ("mid-1970s", "mid-1970s"),
        ("1965-sep", "1965-sep"),
        ("12-19 january 1990", "12-19 january 1990"),
        ("[?]", ""),
    ],
)
def test_strip_noise(text: str, expected: str) -> None:
    assert spaced(strip_noise(text)) == expected


@pytest.mark.parametrize(
    "text, source, expected",
    [
        ("c1977", "marc", "1977"),
        ("c 1977", "marc", "1977"),
        ("c jul 1993", "marc", "jul 1993"),
        ("1890, c1887", "marc", "1890, 1887"),
        ("c1959", "axiell", "~1959"),
        ("c 1959", "axiell", "~1959"),
        ("c jul 1993", "axiell", "~jul 1993"),
        ("c early 1990s", "axiell", "~early 1990s"),
        ("c.1930", "marc", "~1930"),
        ("ca. 1750", "marc", "~1750"),
        ("circa 1750", "marc", "~1750"),
        ("approximately 1800", "marc", "~1800"),
        ("c. april 2007", "marc", "~april 2007"),
        ("century", "marc", "century"),
        ("cent.", "marc", "cent."),
        ("c.1930-c.1985", "marc", "~1930-~1985"),
        ("1750-c. 1760", "marc", "1750-~1760"),
        ("c1977-c1987", "marc", "1977-1987"),
        ("c1977-c1987", "axiell", "~1977-~1987"),
        ("circa1750", "marc", "~1750"),
        ("c.1930s", "marc", "~1930s"),
        ("about early 1800s", "marc", "~early 1800s"),
        ("ca. c. 1750", "marc", "~1750"),
        ("cambridge 1900", "marc", "cambridge 1900"),
        ("c", "axiell", "c"),
        ("c.", "axiell", "c."),
        ("circa", "marc", "circa"),
    ],
)
def test_mark_circa(text: str, source: Source, expected: str) -> None:
    assert mark_circa(text, source) == expected


@pytest.mark.parametrize(
    "text, source, expected",
    [
        ("[1929]", "marc", "1929"),
        ("c1977.", "marc", "1977"),
        ("[ca. 1750?]", "marc", "~1750"),
        ("MDCCLXXV. [1775]", "marc", "1775"),
        ("Anno M.D.XXXI.", "marc", "anno 1531"),
        ("[1900]-[1910]", "marc", "1900-1910"),
        ("1994 -", "marc", "1994-"),
        ("c. early 20th century", "marc", "early 20th century"),
        ("Revolution, 1775-1783", "marc", "revolution 1775-1783"),
        ("Sep-1965", "marc", "sep 1965"),
        ("  19th century.  ", "marc", "19th century"),
        ("c.1955-1984", "axiell", "~1955-1984"),
        ("c 1959", "axiell", "~1959"),
        ("c Jul 1993", "axiell", "~jul 1993"),
        ("", "marc", ""),
        ("[?]", "marc", ""),
        ("n.d.", "marc", "n.d"),
        ("- 1953", "marc", "-1953"),
        ("1500 - 1600", "marc", "1500-1600"),
        ("[1606] [1706]", "marc", "1706"),
        ("1[841-1849]", "marc", "1841-1849"),
        ("19??-1944.", "marc", "20th century-1944"),
        ("ca. c. 1750", "marc", "~1750"),
    ],
)
def test_normalise(text: str, source: Source, expected: str) -> None:
    assert normalise(text, source) == expected
