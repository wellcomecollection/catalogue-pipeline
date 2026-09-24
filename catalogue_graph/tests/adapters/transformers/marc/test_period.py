from datetime import date

import pytest

from adapters.transformers.marc.parsers.period import Source, parse
from adapters.transformers.marc.period import parse_period
from models.pipeline.identifier import Identifiable


@pytest.mark.parametrize(
    "text, start, end",
    [
        ("1984", date(1984, 1, 1), date(1984, 12, 31)),
        ("[1929]", date(1929, 1, 1), date(1929, 12, 31)),
        ("1929?", date(1929, 1, 1), date(1929, 12, 31)),
        ("476.", date(476, 1, 1), date(476, 12, 31)),
        ("1988-1990", date(1988, 1, 1), date(1990, 12, 31)),
        ("1897-99", date(1897, 1, 1), date(1899, 12, 31)),
        ("1750-1", date(1750, 1, 1), date(1751, 12, 31)),
        ("1[841-1849]", date(1841, 1, 1), date(1849, 12, 31)),
        ("between 1870 and 1879", date(1870, 1, 1), date(1879, 12, 31)),
        ("1711/12", date(1711, 1, 1), date(1712, 12, 31)),
        ("1970s", date(1970, 1, 1), date(1979, 12, 31)),
        ("early 1970s", date(1970, 1, 1), date(1973, 12, 31)),
        ("Mid-late 1960s", date(1963, 1, 1), date(1969, 12, 31)),
        ("[199?]", date(1990, 1, 1), date(1999, 12, 31)),
        ("[201 ]", date(2010, 1, 1), date(2019, 12, 31)),
        ("[19--]", date(1900, 1, 1), date(1999, 12, 31)),
        ("1825-[19--?]", date(1825, 1, 1), date(1999, 12, 31)),
        ("19th century.", date(1800, 1, 1), date(1899, 12, 31)),
        ("19th cent.", date(1800, 1, 1), date(1899, 12, 31)),
        ("1st century", date(1, 1, 1), date(99, 12, 31)),
        ("Early 20th century", date(1900, 1, 1), date(1939, 12, 31)),
        ("Mid to late 20th century", date(1930, 1, 1), date(1999, 12, 31)),
        ("19th-20th centuries.", date(1800, 1, 1), date(1999, 12, 31)),
        ("late 19th-early 20th century", date(1860, 1, 1), date(1939, 12, 31)),
        ("14 Nov 2007", date(2007, 11, 14), date(2007, 11, 14)),
        ("19 Dec1936", date(1936, 12, 19), date(1936, 12, 19)),
        ("July 1969-June1981", date(1969, 7, 1), date(1981, 6, 30)),
        ("November 14, 2007", date(2007, 11, 14), date(2007, 11, 14)),
        ("1851 Nov. 27", date(1851, 11, 27), date(1851, 11, 27)),
        ("14/11/2007", date(2007, 11, 14), date(2007, 11, 14)),
        ("May-June 1960", date(1960, 5, 1), date(1960, 6, 30)),
        ("May to June 1960", date(1960, 5, 1), date(1960, 6, 30)),
        ("Spring to autumn 1990", date(1990, 3, 1), date(1990, 11, 30)),
        ("early to mid 1970s", date(1970, 1, 1), date(1976, 12, 31)),
        ("12-19 January 1990", date(1990, 1, 12), date(1990, 1, 19)),
        ("Sep-1965", date(1965, 9, 1), date(1965, 9, 30)),
        ("27-Aug-1917", date(1917, 8, 27), date(1917, 8, 27)),
        ("1887 Nov", date(1887, 11, 1), date(1887, 11, 30)),
        ("1947-mid-1960s", date(1947, 1, 1), date(1966, 12, 31)),
        ("1970-<81>", date(1970, 1, 1), date(1981, 12, 31)),
        ("<1988->", date(1988, 1, 1), date.max),
        ("1985, c1983.", date(1985, 1, 1), date(1985, 12, 31)),
        ("2014, cop. 1991.", date(2014, 1, 1), date(2014, 12, 31)),
        ("Winter 1962", date(1962, 12, 1), date(1963, 2, 28)),
        ("To 1500.", date.min, date(1500, 12, 31)),
        ("Early works to 1800", date.min, date(1800, 12, 31)),
        ("Early to 1800.", date.min, date(1800, 12, 31)),
        ("mid to late 1970s", date(1973, 1, 1), date(1979, 12, 31)),
        ("1990 March 12-", date(1990, 3, 12), date.max),
        ("An XI--1803.", date(1803, 1, 1), date(1803, 12, 31)),
        ("-1953", date.min, date(1953, 12, 31)),
        ("before 1800", date.min, date(1800, 12, 31)),
        ("1994-", date(1994, 1, 1), date.max),
        ("640-.", date(640, 1, 1), date.max),
        ("1920's", date(1920, 1, 1), date(1929, 12, 31)),
        ("[1990's?]", date(1990, 1, 1), date(1999, 12, 31)),
        ("[not before 1898]", date(1898, 1, 1), date.max),
        ("[not after 1850]", date.min, date(1850, 12, 31)),
        ("1900-present.", date(1900, 1, 1), date.max),
        ("1970s-80s", date(1970, 1, 1), date(1989, 12, 31)),
        ("--1797.", date(1797, 1, 1), date(1797, 12, 31)),
        ("1875--85.", date(1875, 1, 1), date(1885, 12, 31)),
        ("[20-?]", date(2000, 1, 1), date(2099, 12, 31)),
        ("[19-]", date(1900, 1, 1), date(1999, 12, 31)),
        ("18-19th century", date(1700, 1, 1), date(1899, 12, 31)),
        ("after 1817", date(1817, 1, 1), date.max),
        ("1709 [1710]", date(1710, 1, 1), date(1710, 12, 31)),
        ("5782 [i.e. 1782]", date(1782, 1, 1), date(1782, 12, 31)),
        ("M.DCC.XLV.", date(1745, 1, 1), date(1745, 12, 31)),
        ("MDLXII", date(1562, 1, 1), date(1562, 12, 31)),
        ("Anno M.D.XXXI.", date(1531, 1, 1), date(1531, 12, 31)),
        ("M.DCC.XLV.-M.DCC.L.", date(1745, 1, 1), date(1750, 12, 31)),
        ("MDCCLXXVIII. [1787]", date(1787, 1, 1), date(1787, 12, 31)),
        ("MDCCXCVI, 1796.", date(1796, 1, 1), date(1796, 12, 31)),
        ("1854 [ie 1855]", date(1855, 1, 1), date(1855, 12, 31)),
        ("Anno dñi M.D.xxviij [1528]", date(1528, 1, 1), date(1528, 12, 31)),
        ("MDCCLXXXVIII.-MDCCLXXXIX. [1788-1789]", date(1788, 1, 1), date(1789, 12, 31)),
        ("Revolution, 1775-1783", date(1775, 1, 1), date(1783, 12, 31)),
        ("29 February 1975", date(1975, 1, 1), date(1975, 12, 31)),
        ("31/04/1994", date(1994, 1, 1), date(1994, 12, 31)),
        ("1/94 [January 1994]", date(1994, 1, 1), date(1994, 1, 31)),
        ("Aug. 67 [August 1967]", date(1967, 8, 1), date(1967, 8, 31)),
        ("c. 005", date(1, 1, 1), date(14, 12, 31)),
        ("c1977.", date(1977, 1, 1), date(1977, 12, 31)),
        ("©1981", date(1981, 1, 1), date(1981, 12, 31)),
        ("[ca. 1750?]", date(1740, 1, 1), date(1759, 12, 31)),
        ("circ. 1750", date(1740, 1, 1), date(1759, 12, 31)),
        ('"c. 1990"', date(1980, 1, 1), date(1999, 12, 31)),
        ("mid.-18th century", date(1730, 1, 1), date(1769, 12, 31)),
        ("31October 1820", date(1820, 10, 31), date(1820, 10, 31)),
        ("12december1992", date(1992, 12, 12), date(1992, 12, 12)),
        ("ca. c. 1750", date(1740, 1, 1), date(1759, 12, 31)),
        ("[approximately 1800?]", date(1790, 1, 1), date(1809, 12, 31)),
        ("c.1960s", date(1950, 1, 1), date(1979, 12, 31)),
        ("c. 18th century", date(1690, 1, 1), date(1809, 12, 31)),
        ("c.1955-1984", date(1945, 1, 1), date(1984, 12, 31)),
        ("c.1955-c.1984", date(1945, 1, 1), date(1993, 12, 31)),
        ("c. April 2007", date(2007, 4, 1), date(2007, 4, 30)),
        ("c Jul 1993", date(1993, 7, 1), date(1993, 7, 31)),
        ("c. early 20th century", date(1900, 1, 1), date(1939, 12, 31)),
        ("pre 1900", date(1890, 1, 1), date(1900, 12, 31)),
        ("(post-1965)", date(1965, 1, 1), date(1974, 12, 31)),
    ],
)
def test_parse(text: str, start: date, end: date) -> None:
    assert parse(text) == (start, end)


@pytest.mark.parametrize(
    "text",
    [
        "Ancient",
        "mid century",
        "n.d.",
        "[date of publication not identified]",
        "1820 or 1821",
        "1911, 1913",
        "1657-1562.",
        "2971",
        "12345",
        "pre 1st century",
        "Nov 0000",
        "Nov 3000",
        "6400s",
        "22nd century",
        "31/12/2500",
    ],
)
def test_parse_nothing(text: str) -> None:
    assert parse(text) is None


@pytest.mark.parametrize(
    "source, start, end",
    [
        ("marc", date(1959, 1, 1), date(1959, 12, 31)),
        ("axiell", date(1949, 1, 1), date(1968, 12, 31)),
    ],
)
def test_bare_c_is_copyright_in_marc_and_circa_in_axiell(
    source: Source, start: date, end: date
) -> None:
    assert parse("c1959", source) == (start, end)


def test_parse_period_range() -> None:
    period = parse_period("1988-1990")
    assert period.label == "1988-1990"
    assert period.range is not None
    assert period.range.label == "1988-1990"
    assert period.range.from_time == "1988-01-01T00:00:00Z"
    assert period.range.to_time == "1990-12-31T23:59:59.999999999Z"


def test_parse_period_open_range() -> None:
    period = parse_period("To 1500.")
    assert period.range is not None
    assert period.range.from_time == "0001-01-01T00:00:00Z"
    assert period.range.to_time == "1500-12-31T23:59:59.999999999Z"


def test_parse_period_without_range() -> None:
    period = parse_period("mid century")
    assert period.label == "mid century"
    assert period.range is None


def test_parse_period_keeps_identifier() -> None:
    identifier = Identifiable.identifier_from_text("1988-1990", "Period")
    assert parse_period("1988-1990", identifier=identifier).id == identifier
