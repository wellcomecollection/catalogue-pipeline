"""Period parser: a few exact date atoms, a few range forms, then year extraction.

parse_period wraps the result as a Period; parse gives the bare (start, end) span:

parse("14 Nov 2007")       -> (date(2007, 11, 14), date(2007, 11, 14))
parse("1970s")             -> (date(1970, 1, 1), date(1979, 12, 31))
parse("May-June 1960")     -> (date(1960, 5, 1), date(1960, 6, 30))
parse("19th-20th centuries") -> (date(1800, 1, 1), date(1999, 12, 31))
parse("[199?]")            -> (date(1990, 1, 1), date(1999, 12, 31))
parse("c.1955-1984", "axiell") -> (date(1945, 1, 1), date(1984, 12, 31))
parse("Ancient")           -> None
"""

import calendar
import re
from datetime import date

from models.pipeline.concept import DateTimeRange, Period
from models.pipeline.identifier import Identifiable, Unidentifiable

MONTHS = {m.lower(): i for i, m in enumerate(calendar.month_name) if m}
MONTHS |= {m.lower(): i for i, m in enumerate(calendar.month_abbr) if m} | {"sept": 9}
MONTH = r"(" + "|".join(sorted(MONTHS, key=len, reverse=True)) + r")\.?"
ORD = r"(?:st|nd|rd|th)"
YEAR = r"(\d{4})"
DAY = rf"(\d{{1,2}}){ORD}?"
QUAL = r"(early|middle|mid|late)"

# early / mid / late thirds of a century (years 0-39, 30-69, 60-99) and of a decade (0-3, 3-6, 6-9)
CENTURY_PART = {
    None: (0, 99),
    "early": (0, 39),
    "mid": (30, 69),
    "middle": (30, 69),
    "late": (60, 99),
}
DECADE_PART = {
    None: (0, 9),
    "early": (0, 3),
    "mid": (3, 6),
    "middle": (3, 6),
    "late": (6, 9),
}

ROMAN = {"m": 1000, "d": 500, "c": 100, "l": 50, "x": 10, "v": 5, "i": 1}
SEASON = {
    "spring": (3, 5),
    "summer": (6, 8),
    "autumn": (9, 11),
    "fall": (9, 11),
    "winter": (12, 2),
}

Span = tuple[date, date]  # inclusive
MIN, MAX = date(1, 1, 1), date(9999, 12, 31)
LATEST_YEAR = 2040  # anything later is a typo or a Hebrew-calendar year


def normalise(text: str, source: str) -> str:
    """Lower-case, drop noise, mark circa dates with ~ and rewrite the forms that stand in for a date."""
    text = text.lower()
    # everything up to and including "i.e." goes: the correction after it replaces the transcribed date
    text = re.sub(r"^.*?\bi\.\s?e\.", "", text)
    # a year followed by a bracketed year, "1709 [1710]" or "1709. [1710?]": keep only the bracketed correction
    text = re.sub(r"\b\d{4}[.,]?\s*\[(\d{4})\??\]", r"[\1]", text)
    # three digits then a placeholder, "199?", "192-" or "[201 ]": rewrite as the decade "1990s"
    text = re.sub(r"\b(\d{3})(?:[-?](?!\d)| (?=[\].]))", r"\g<1>0s", text)
    # two digits then a placeholder, "19--", "19??" or "[19 ]": rewrite as "20th century"
    text = re.sub(
        r"\b(\d{2})(?:--|\?\?| (?=\]))", lambda m: f"{int(m[1]) + 1}th century", text
    )
    # a bracket touching a digit, "174[2]" or "1[7]17", is a typo: drop it
    text = re.sub(r"(?<=\d)[\[\]]|[\[\]](?=\d)", "", text)
    # remaining brackets, parentheses, question marks and copyright signs are noise
    text = re.sub(r"[\[\]()?©]", " ", text)
    # a comma not directly after a year, "Revolution, 1775" or "March 8, 1800", is noise; "1719, 1720" keeps its comma
    text = re.sub(r"(?<!\d{4}),", " ", text)
    # a month joined to its year by a hyphen, "Sep-1965": separate them so the hyphen is not read as a range
    text = re.sub(rf"\b{MONTH}-(?=\d{{4}}\b)", r"\1 ", text)
    # a circa word before a digit or a month name becomes "~"; a bare "c" before digits, or a lone "c"
    # before a word, is copyright in marc (dropped) and circa in axiell (becomes "~")
    text = re.sub(
        r"\b(c\.|ca\.|circa|approximately|about|approx\.?)\s*(?=[\da-z])|\bc(?:\s?(?=\d)| (?=[a-z]))",
        lambda m: "~" if m.group(1) or source == "axiell" else "",
        text,
    )
    # a whole string of roman numerals starting with m and containing c, "m.dcc.xlv": a year of 1100 to 1999
    if re.fullmatch(r"\s*m[mdclxvi.,\s]*c[mdclxvi.,\s]*", text):
        text = str(roman(text))
    # "~" before early / mid / late, "c. early 20th century": the subrange is the answer, no widening
    text = re.sub(r"~(?=early|mid|late)", "", text)
    text = re.sub(r"\s*-\s*", "-", text)
    return re.sub(r"\s+", " ", text).strip(" .")


def roman(text: str) -> int:
    values = [ROMAN[ch] for ch in text if ch in ROMAN]
    return sum(
        -v if i + 1 < len(values) and v < values[i + 1] else v
        for i, v in enumerate(values)
    )


def end_of_month(year: int, month: int) -> date:
    return date(year, month, calendar.monthrange(year, month)[1])


def single_day(year: int, month: int, day: int) -> Span | None:
    try:
        d = date(year, month, day)
    except ValueError:  # "29 February 1975", "31/04/1994"
        return None
    return d, d


# Each atom matches a whole string and returns an inclusive (start, end) span.
def atom(text: str) -> Span | None:
    if text.startswith("~"):
        # circa: a year widens by 10 before and 9 after, a decade or century by 10 at each end;
        # months, days and seasons are not widened
        span = atom(text[1:].strip())
        if span and span == widen(span, 0, 0):  # whole years only
            return widen(span, -10, 9 if span[0].year == span[1].year else 10)
        return span
    # "1984", "476"
    if re.fullmatch(r"\d{3,4}", text) and 0 < int(text) <= LATEST_YEAR:
        return date(int(text), 1, 1), date(int(text), 12, 31)
    # "1970s", "early 1970s"
    if (m := re.fullmatch(rf"(?:{QUAL} )?(\d{{3}})0s", text)) and int(m[2]) > 0:
        y, (lo, hi) = int(m[2]) * 10, DECADE_PART[m[1]]
        return date(y + lo, 1, 1), date(y + hi, 12, 31)
    # "19th century", "19 cent.", "mid-19th century", "mid to late 19th century"
    if (
        m := re.fullmatch(
            rf"(?:{QUAL}(?:[ -]?to[ -]|[ -]))?(?:{QUAL} )?(\d{{1,2}}){ORD}? ?cent(?:ury|\.)?",
            text,
        )
    ) and int(m[3]) > 0:
        start = (int(m[3]) - 1) * 100
        # "mid to late" spans mid's start to late's end
        lo, hi = CENTURY_PART[m[1] or m[2]][0], CENTURY_PART[m[2] or m[1]][1]
        # the 1st century starts in year 1
        return date(max(start + lo, 1), 1, 1), date(start + hi, 12, 31)
    # "winter 1962": winter runs into the next year
    if m := re.fullmatch(rf"({'|'.join(SEASON)}) {YEAR}", text):
        y, (first, last) = int(m[2]), SEASON[m[1]]
        return date(y, first, 1), end_of_month(y + (last < first), last)
    # "nov 2007", "november 2007"
    if m := re.fullmatch(rf"{MONTH} {YEAR}", text):
        y, mo = int(m[2]), MONTHS[m[1]]
        return date(y, mo, 1), end_of_month(y, mo)
    # "14 nov 2007", "14th nov. 2007"
    if m := re.fullmatch(rf"{DAY} {MONTH} {YEAR}", text):
        return single_day(int(m[3]), MONTHS[m[2]], int(m[1]))
    # "november 14 2007" (the comma is already gone)
    if m := re.fullmatch(rf"{MONTH} {DAY} {YEAR}", text):
        return single_day(int(m[3]), MONTHS[m[1]], int(m[2]))
    # "1851 nov 27"
    if m := re.fullmatch(rf"{YEAR} {MONTH} {DAY}", text):
        return single_day(int(m[1]), MONTHS[m[2]], int(m[3]))
    # "14/11/2007", "14.11.2007": day first
    if m := re.fullmatch(r"(\d{1,2})[/.](\d{1,2})[/.](\d{4})", text):
        return single_day(int(m[3]), int(m[2]), int(m[1]))
    return None


def widen(span: Span, before: int, after: int) -> Span:
    start_year = max(span[0].year + before, MIN.year)
    end_year = min(span[1].year + after, MAX.year)
    return date(start_year, 1, 1), date(end_year, 12, 31)


def parse(text: str, source: str = "marc") -> Span | None:
    """Inclusive (start, end) dates for a period string, or None if it says nothing datable.

    `source` is "marc" or "axiell" and decides what a bare "c" before a year means. In MARC it is a
    copyright date (AACR2 1.4F6 writes c1963; the 008 codes such dates as a plain single year). In
    Axiell it means circa: 99% of Axiell c-dates carry 046 dates ten years either side of the year.
    """
    text = normalise(text, source)
    # "1820 or 1821", "1719, 1720": two dates offered, not a range. In the FOLIO 008, ascending comma
    # pairs are coded as a range 72% of the time but as a single date 18%, and descending pairs are
    # reprint or copyright pairs where the first year alone is right. Neither reading is safe, so
    # neither is made; production falls back to the 008 range.
    if " or " in text or re.search(r"\d{4}\s*,\s*\D*\d{4}", text):
        return None
    if span := atom(text):
        return span
    # open start: anything ending in "to X", or "before X", or "-X": "To 1500", "Early works to 1800", "-1953"
    if (m := re.fullmatch(r"(?:[^\d]*\bto|before|-)\s?(.+)", text)) and (
        span := atom(m[1])
    ):
        return MIN, span[1]
    # "pre 1900", "post-1965": ten years before, or nine years after
    if (m := re.fullmatch(r"(pre|post)[- ](.+)", text)) and (span := atom(m[2])):
        return widen(span, -10, 0) if m[1] == "pre" else widen(span, 0, 9)
    # open end: "after 1817", "1994-"
    if (m := re.fullmatch(r"after (.+)|(.+?)-", text)) and (span := atom(m[1] or m[2])):
        return span[0], MAX
    # "X-Y", "between X and Y", "X to Y", or "X/Y" when there is no hyphen: a range of two atoms
    if (
        m := re.fullmatch(r"(?:between )?(.+?)(?:-| and | to )(.+)|(.+?)/(.+)", text)
    ) and (span := range_of(m[1] or m[3], m[2] or m[4])):
        return span if span[0] <= span[1] else None
    return fallback(text)


def range_of(left: str, right: str) -> Span | None:
    """Two atoms joined; a left side without a year borrows the tail of the right side."""
    right = right.replace("centuries", "century")
    # a right side of one or two digits, "1897-99" or "1750-1": complete it from the left year
    if re.fullmatch(r"\d{1,2}", right) and (year := re.search(YEAR, left)):
        right = year[1][: 4 - len(right)] + right
    # "may-june 1960", "12-19 january 1990", "late 19th-early 20th century": the left takes what the right
    # has beyond the left's own words, "1960", "january 1990", "century"
    if not (re.search(YEAR, left) or atom(left)):
        left = " ".join([left, *(right.split()[len(left.split()) :] or [right])])
    a, b = atom(left) or fallback(left), atom(right) or fallback(right)
    return (a[0], b[1]) if a and b else None


def fallback(text: str) -> Span | None:
    """Span the four-digit years present, if any."""
    # exactly four digits, not preceded by "+": "U+2019" in "Coup d U+2019 état, 1797" is not a year
    years = [
        int(y)
        for y in re.findall(r"(?<![\d+])\d{4}(?!\d)", text)
        if 0 < int(y) <= LATEST_YEAR
    ]
    if not years:
        return None
    return date(min(years), 1, 1), date(max(years), 12, 31)


def parse_period(
    label: str,
    identifier: Identifiable | Unidentifiable | None = None,
    source: str = "marc",
) -> Period:
    """A Period for the label, with a range when the label can be read as dates; `source` as in `parse`."""
    span = parse(label, source)
    date_range = (
        DateTimeRange(
            label=label,
            **{
                "from": span[0].isoformat() + "T00:00:00Z",
                # the Scala pipeline's end of day has nanosecond precision
                "to": span[1].isoformat() + "T23:59:59.999999999Z",
            },
        )
        if span
        else None
    )
    return Period(label=label, range=date_range, id=identifier or Unidentifiable())
