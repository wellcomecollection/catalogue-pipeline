"""Turn a free-text period, "c.1955-1984" or "Mid 19th century", into inclusive (start, end) dates.

The work happens in four stages, each a function below:

1. `normalise` lower-cases the text, takes cataloguers' corrections, rewrites placeholder digits,
   strips noise and marks circa dates with a leading "~".
2. `atom` reads a whole string as one date expression: a year, decade, century, season, month or day.
3. `open_range` and `closed_range` read strings with one or two atoms joined by "to", "-", "and" or "/".
4. `fallback` spans whatever four-digit years are left when nothing else matched.

parse("14 Nov 2007")           -> (date(2007, 11, 14), date(2007, 11, 14))
parse("1970s")                 -> (date(1970, 1, 1), date(1979, 12, 31))
parse("May-June 1960")         -> (date(1960, 5, 1), date(1960, 6, 30))
parse("19th-20th centuries")   -> (date(1800, 1, 1), date(1999, 12, 31))
parse("[199?]")                -> (date(1990, 1, 1), date(1999, 12, 31))
parse("c.1955-1984", "axiell") -> (date(1945, 1, 1), date(1984, 12, 31))
parse("Ancient")               -> None
"""

import calendar
import re
from datetime import date

Span = tuple[date, date]
MIN, MAX = date(1, 1, 1), date(9999, 12, 31)
LATEST_YEAR = 2040  # anything later is a typo or a Hebrew-calendar year

# Regex fragments. MONTH matches full and abbreviated English names with an optional dot.
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

# Seasons as (first month, last month); winter runs into the following year.
SEASON = {
    "spring": (3, 5),
    "summer": (6, 8),
    "autumn": (9, 11),
    "fall": (9, 11),
    "winter": (12, 2),
}
ROMAN = {"m": 1000, "d": 500, "c": 100, "l": 50, "x": 10, "v": 5, "i": 1}


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
    if span := open_range(text):
        return span
    # "X-Y", "between X and Y", "X to Y", or "X/Y" when there is no hyphen
    if (
        m := re.fullmatch(r"(?:between )?(.+?)(?:-| and | to )(.+)|(.+?)/(.+)", text)
    ) and (span := closed_range(m[1] or m[3], m[2] or m[4])):
        # a range that runs backwards, "1657-1562", is refused rather than guessed at
        return span if span[0] <= span[1] else None
    return fallback(text)


# --- stage 1: normalise -------------------------------------------------------------------------


def normalise(text: str, source: str) -> str:
    """Lower-case, take corrections, expand placeholders, drop noise and mark circa dates with "~"."""
    text = take_corrections(text.lower())
    text = expand_placeholders(text)
    text = strip_noise(text)
    text = mark_circa(text, source)
    # a whole string of roman numerals starting with m and containing c, "m.dcc.xlv": a year of 1100 to 1999
    if re.fullmatch(r"\s*m[mdclxvi.,\s]*c[mdclxvi.,\s]*", text):
        text = str(roman(text))
    # "~" before early / mid / late, "c. early 20th century": the subrange is the answer, no widening
    text = re.sub(r"~(?=early|mid|late)", "", text)
    text = re.sub(r"\s*-\s*", "-", text)
    return re.sub(r"\s+", " ", text).strip(" .")


def take_corrections(text: str) -> str:
    """Keep only the cataloguer's correction where one is given."""
    # everything up to and including "i.e." goes: the correction after it replaces the transcribed date
    text = re.sub(r"^.*?\bi\.\s?e\.", "", text)
    # a year followed by a bracketed year, "1709 [1710]" or "1709. [1710?]": keep only the bracketed correction
    return re.sub(r"\b\d{4}[.,]?\s*\[(\d{4})\??\]", r"[\1]", text)


def expand_placeholders(text: str) -> str:
    """Rewrite years with unknown final digits as the decade or century they stand for."""
    # three digits then a placeholder, "199?", "192-" or "[201 ]": rewrite as the decade "1990s"
    text = re.sub(r"\b(\d{3})(?:[-?](?!\d)| (?=[\].]))", r"\g<1>0s", text)
    # two digits then a placeholder, "19--", "19??" or "[19 ]": rewrite as "20th century"
    return re.sub(
        r"\b(\d{2})(?:--|\?\?| (?=\]))", lambda m: f"{int(m[1]) + 1}th century", text
    )


def strip_noise(text: str) -> str:
    """Drop the punctuation that carries no date information."""
    # a bracket touching a digit, "174[2]" or "1[7]17", is a typo: drop it
    text = re.sub(r"(?<=\d)[\[\]]|[\[\]](?=\d)", "", text)
    # remaining brackets, parentheses, question marks and copyright signs are noise
    text = re.sub(r"[\[\]()?©]", " ", text)
    # a comma not directly after a year, "Revolution, 1775" or "March 8, 1800", is noise; "1719, 1720" keeps its comma
    text = re.sub(r"(?<!\d{4}),", " ", text)
    # a month joined to its year by a hyphen, "Sep-1965": separate them so the hyphen is not read as a range
    return re.sub(rf"\b{MONTH}-(?=\d{{4}}\b)", r"\1 ", text)


def mark_circa(text: str, source: str) -> str:
    """Replace circa markers with "~" and drop copyright markers, so `atom` sees one convention."""
    # a circa word before a digit or a month name becomes "~"; a bare "c" before digits, or a lone "c"
    # before a word, is copyright in marc (dropped) and circa in axiell (becomes "~")
    return re.sub(
        r"\b(c\.|ca\.|circa|approximately|about|approx\.?)\s*(?=[\da-z])|\bc(?:\s?(?=\d)| (?=[a-z]))",
        lambda m: "~" if m.group(1) or source == "axiell" else "",
        text,
    )


def roman(text: str) -> int:
    values = [ROMAN[ch] for ch in text if ch in ROMAN]
    return sum(
        -v if i + 1 < len(values) and v < values[i + 1] else v
        for i, v in enumerate(values)
    )


# --- stage 2: atoms -------------------------------------------------------------------------------
# Each atom reads a whole normalised string as one date expression, or returns None.


def atom(text: str) -> Span | None:
    if text.startswith("~"):
        return circa(atom(text[1:].strip()))
    for rule in (year, decade, century, season, month, day):
        if span := rule(text):
            return span
    return None


def year(text: str) -> Span | None:
    """ "1984", "476"."""
    if re.fullmatch(r"\d{3,4}", text) and 0 < int(text) <= LATEST_YEAR:
        return date(int(text), 1, 1), date(int(text), 12, 31)
    return None


def decade(text: str) -> Span | None:
    """ "1970s", "early 1970s"."""
    m = re.fullmatch(rf"(?:{QUAL} )?(\d{{3}})0s", text)
    if not m or int(m[2]) == 0:  # "0000s" would start in year 0
        return None
    start, (lo, hi) = int(m[2]) * 10, DECADE_PART[m[1]]
    return date(start + lo, 1, 1), date(start + hi, 12, 31)


def century(text: str) -> Span | None:
    """ "19th century", "19 cent.", "mid-19th century", "mid to late 19th century"."""
    m = re.fullmatch(
        rf"(?:{QUAL}(?:[ -]?to[ -]|[ -]))?(?:{QUAL} )?(\d{{1,2}}){ORD}? ?cent(?:ury|\.)?",
        text,
    )
    if not m or int(m[3]) == 0:  # there is no 0th century
        return None
    start = (int(m[3]) - 1) * 100
    # "mid to late" spans mid's start to late's end
    lo, hi = CENTURY_PART[m[1] or m[2]][0], CENTURY_PART[m[2] or m[1]][1]
    # the 1st century starts in year 1
    return date(max(start + lo, 1), 1, 1), date(start + hi, 12, 31)


def season(text: str) -> Span | None:
    """ "winter 1962", which runs into 1963."""
    if m := re.fullmatch(rf"({'|'.join(SEASON)}) {YEAR}", text):
        y, (first, last) = int(m[2]), SEASON[m[1]]
        return date(y, first, 1), end_of_month(y + (last < first), last)
    return None


def month(text: str) -> Span | None:
    """ "nov 2007", "november 2007"."""
    if m := re.fullmatch(rf"{MONTH} {YEAR}", text):
        y, mo = int(m[2]), MONTHS[m[1]]
        return date(y, mo, 1), end_of_month(y, mo)
    return None


def day(text: str) -> Span | None:
    """A single day in any of the orders found in the data."""
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


def circa(span: Span | None) -> Span | None:
    """Widen an approximate span: a year by 10 before and 9 after, a decade or century by 10 at each end."""
    if span is None or span != widen(
        span, 0, 0
    ):  # months, days and seasons are not widened
        return span
    return widen(span, -10, 9 if span[0].year == span[1].year else 10)


def widen(span: Span, before: int, after: int) -> Span:
    start_year = max(span[0].year + before, MIN.year)
    end_year = min(span[1].year + after, MAX.year)
    return date(start_year, 1, 1), date(end_year, 12, 31)


def end_of_month(year: int, month: int) -> date:
    return date(year, month, calendar.monthrange(year, month)[1])


def single_day(year: int, month: int, day: int) -> Span | None:
    try:
        d = date(year, month, day)
    except ValueError:  # "29 February 1975", "31/04/1994"
        return None
    return d, d


# --- stage 3: ranges ------------------------------------------------------------------------------


def open_range(text: str) -> Span | None:
    """An atom with one side left open."""
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
    return None


def closed_range(left: str, right: str) -> Span | None:
    """Two atoms joined, from the start of the left one to the end of the right one."""
    right = complete_short_year(left, right.replace("centuries", "century"))
    left = borrow_from_right(left, right)
    a, b = atom(left) or fallback(left), atom(right) or fallback(right)
    return (a[0], b[1]) if a and b else None


def complete_short_year(left: str, right: str) -> str:
    """ "1897-99" and "1750-1": a right side of one or two digits takes its leading digits from the left year."""
    if re.fullmatch(r"\d{1,2}", right) and (year := re.search(YEAR, left)):
        return year[1][: 4 - len(right)] + right
    return right


def borrow_from_right(left: str, right: str) -> str:
    """A left side that is not a date on its own takes the words the right side has beyond its own count.

    "may-june 1960" gives "may 1960", "12-19 january 1990" gives "12 january 1990", and
    "late 19th-early 20th century" gives "late 19th century". A right side with no extra words is
    taken whole, so "mid-1970s" gives "mid 1970s".
    """
    if re.search(YEAR, left) or atom(left):
        return left
    return " ".join([left, *(right.split()[len(left.split()) :] or [right])])


# --- stage 4: fallback ----------------------------------------------------------------------------


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
