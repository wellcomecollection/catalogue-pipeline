"""Turn a free-text period, "c.1955-1984" or "Mid 19th century", into inclusive (start, end) dates.

Parsing happens in four stages, each of which is a function below:

1. `normalise` lower-cases the text, writes roman numerals as numbers, takes cataloguers'
   corrections, rewrites placeholder digits, strips noise and marks circa dates with a leading "~".
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

Departures from the Scala PeriodParser:
    - "1900s" and "2000s" are decades (1900-1909), not centuries
    - Years before 1 AD, "30 B.C.", give None, because `datetime.date` cannot represent them
    - Two years joined by a comma, "1719, 1720", give None rather than a span covering both
    - A bare "c" before a year in MARC is a copyright date, so "c1977" is 1977, not 1967-1986
"""

import calendar
import re
from datetime import date, timedelta
from typing import Literal

from adapters.transformers.marc.parsers.roman import roman_numeral

Span = tuple[date, date]
Source = Literal["marc", "axiell"]
MIN, MAX = date(1, 1, 1), date(9999, 12, 31)
LATEST_YEAR = 2040  # anything later is a typo or a Hebrew-calendar year


def plausible(year: int) -> bool:
    """Every year the parser produces passes this check, whatever form it was written in."""
    return 0 < year <= LATEST_YEAR


# The vocabulary, and the regex fragments built from it.
# MONTH matches full and abbreviated English names with an optional dot.
MONTHS = {m.lower(): i for i, m in enumerate(calendar.month_name) if m}
MONTHS |= {m.lower(): i for i, m in enumerate(calendar.month_abbr) if m} | {"sept": 9}
SEASONS = {
    "spring": (3, 5),
    "summer": (6, 8),
    "autumn": (9, 11),
    "fall": (9, 11),
    "winter": (12, 2),
}
QUALIFIERS = {"early", "middle", "mid", "late"}
RANGE_WORDS = {"to", "and", "before", "after", "not", "pre", "post", "between"}
DATE_WORDS = set(MONTHS) | set(SEASONS) | QUALIFIERS | RANGE_WORDS

MONTH = "(" + "|".join(sorted(MONTHS, key=len, reverse=True)) + r")\.?"
QUAL = "(" + "|".join(sorted(QUALIFIERS, key=len, reverse=True)) + ")"
QUALIFIED = rf"(?:{QUAL}\.?(?:[ -]?to[ -]|[ -]))?(?:{QUAL}\.?[ -])?"  # "mid ", "mid-", "mid to late "
ORD = r"(?:st|nd|rd|th)"
YEAR = r"(\d{4})"
DAY = rf"(\d{{1,2}}){ORD}?"

# early / mid / late as tenths of a decade or century: years 0-3, 3-6 and 6-9 of a decade, 00-39,
# 30-69 and 60-99 of a century
PART = {
    None: (0, 10),
    "early": (0, 4),
    "mid": (3, 7),
    "middle": (3, 7),
    "late": (6, 10),
}


def parse(text: str, source: Source = "marc") -> Span | None:
    """Inclusive (start, end) dates for a period string, or None if it says nothing datable.

    `source` matters for one thing: a bare "c" before a year. In MARC it marks a copyright date,
    so "c1963" is the year 1963. In Axiell it means circa, so "c1930" is widened like any other
    approximate date (see `circa`).
    """
    text = normalise(text, source)

    # Two dates like "1820 or 1821" or "1719, 1720" are ambiguous. A comma pair can be a range, but
    # it can also be a publication year followed by an earlier copyright or original year, so no
    # range is returned and production falls back to the 008 date where available. The same year
    # twice, "MDCCXCVI, 1796", is not a pair.
    if " or " in text or re.search(r"(\d{4})\s*,\s*\D*(?!\1)\d{4}", text):
        return None
    span = atom(text) or open_range(text) or closed_range(text) or fallback(text)
    # a range that runs backwards, "1657-1562" or "late to early 1970s", is dropped
    return span if span and span[0] <= span[1] else None


# --- stage 1: normalise -------------------------------------------------------------------------


def normalise(text: str, source: Source) -> str:
    """Lower-case, write roman numerals as numbers, take corrections, expand placeholders, drop noise,
    mark circa dates with "~" and drop leading words that are not part of the date."""
    text = convert_roman_numerals(text.lower())
    text = take_corrections(text)
    text = expand_placeholders(text)
    text = strip_noise(text)
    text = mark_circa(text, source)
    # "c. early 20th century": the early / mid / late part is kept and the circa marker dropped, so it is not widened
    text = re.sub(r"~(?=early|mid|late)", "", text)
    text = re.sub(r"\s*-\s*", "-", text)
    # "mid-1970s-1980s": a qualifier's own hyphen is not a range separator
    text = re.sub(r"\b(early|middle|mid|late)\.?-(?=\d)", r"\1 ", text)
    text = re.sub(r"\s+", " ", text).strip(" .")
    return drop_leading_words(text)


def drop_leading_words(text: str) -> str:
    """Drop leading words that are not part of the date, so "revolution 1775-1783" reads as
    "1775-1783", "printed in october 1789" as "october 1789" and "n.d. ~1984" as "~1984"."""
    words = text.split(" ")
    first_date_at = next((i for i, w in enumerate(words) if not is_plain_word(w)), None)
    if first_date_at is None:
        return text  # no parsable date ("ancient", "no date")
    # keep the run of date words leading up to the first date token: "mid to late" before
    # "20th century", but not "middle ages" before "500-1500"
    keep = first_date_at
    while keep > 0 and is_date_word(words[keep - 1]):
        keep -= 1
    return " ".join(words[keep:])


def is_plain_word(token: str) -> bool:
    """Letters, with dots or apostrophes, and at most a trailing comma: "revolution", "n.d.", "period,"."""
    return re.fullmatch(r"[a-z][a-z'.]*,?", token) is not None


def is_date_word(token: str) -> bool:
    return token.strip(".,") in DATE_WORDS


def convert_roman_numerals(text: str) -> str:
    """Write each roman numeral of two or more letters as a number, "anno m.dcc.xlv" as "anno 1745"."""
    # Identify candidate roman numerals via a regex and send them to `roman_numeral`, which converts
    # them to integers, returning `None` for invalid candidates like "civil" or "mill". A candidate
    # may not start inside an abbreviation such as "n.d. c.".
    return re.sub(
        r"(?<![a-z]\.)\b[mdclxvi][mdclxvij.,\s]*[mdclxvij]\b",
        lambda m: str(roman_numeral(m[0]) or m[0]),
        text,
    )


def take_corrections(text: str) -> str:
    """Keep only the cataloguer's correction where one is given."""
    # everything up to and including "i.e.", also written "ie." or "i.e", goes when a date follows it:
    # the correction replaces the transcribed date
    text = re.sub(r"^.*?\bi\.?\s?e\b\.?(?=.*\d)", "", text)
    # a number followed by a bracketed year, "1709 [1710]", "1709. [1710?]" or "[1606] [1706]": keep only
    # the bracketed correction. A hyphen between them, "1700-[1703]", is a range, not a correction.
    text = re.sub(r"\b\d{3,4}[.,'\s\]]*\[(\d{4})\??\]", r"[\1]", text)
    # an abbreviated date followed by its bracketed expansion, "Aug. 67 [August 1967]" or
    # "1/94 [January 1994]": keep only the expansion
    return re.sub(
        rf"^\[?[a-z.\s]*\d{{1,2}}(?:/\d{{2,4}})?\.?\s*\(?\[({MONTH} ?\d{{4}})\??\]",
        r"[\1]",
        text,
    )


def expand_placeholders(text: str) -> str:
    """Rewrite years with unknown final digits as the decade or century they stand for."""
    # three digits then a placeholder, "199?", "192-" or "[201 ]": rewrite as the decade "1990s";
    # "640-" is an open range from the year 640, since there is no decade 6400s
    text = re.sub(
        r"\b(\d{3})(?:[-?](?!\d)| (?=[\].]))",
        lambda m: f"{m[1]}0s" if plausible(int(m[1]) * 10) else m[0],
        text,
    )
    # two digits then a placeholder, "19--", "19??" or "[19 ]", or "[19-?]" and "19-" where nothing
    # but a bracket precedes the digits: rewrite as "20th century"
    return re.sub(
        r"\b(\d{2})(?:--(?!\s*\d)|\?\?| (?=\]))|(?<![^\[])(\d{2})-\??(?=\]|$)",
        lambda m: (
            f"{int(m[1] or m[2]) + 1}th century"
            if plausible(int(m[1] or m[2]) * 100)
            else m[0]
        ),
        text,
    )


def strip_noise(text: str) -> str:
    """Drop the punctuation that carries no date information."""
    # a bracket touching a digit, "174[2]" or "1[7]17", is a typo: drop it
    text = re.sub(r"(?<=\d)[\[\]]|[\[\]](?=\d)", "", text)
    # remaining brackets, parentheses, quotation marks, question marks and copyright signs are noise
    text = re.sub(r"[\[\]()<>?©\"]", " ", text)
    # "1920's": the apostrophe is not part of the decade
    text = re.sub(r"(\d)'s\b", r"\1s", text)
    # runs of hyphens: leading or trailing ones are noise, "--1797." is 1797; inside, "1875--85" is one range
    text = re.sub(r"^\s*-{2,}|-{2,}\s*$", " ", text)
    text = re.sub(r"-{2,}", "-", text)
    # a comma not directly after a year, "Revolution, 1775" or "March 8, 1800", is noise; "1719, 1720" keeps its comma
    text = re.sub(r"(?<!\d{4}),", " ", text)
    # a month joined to its year by a hyphen, "Sep-1965": separate them so the hyphen is not read as a range
    text = re.sub(rf"\b{MONTH}-(?=\d{{4}}\b)", r"\1 ", text)
    # a month, day or range word run into what follows, "Dec1936", "31October" or "before1965": put the space back
    text = re.sub(rf"(\d)(?={MONTH})", r"\1 ", text)
    text = re.sub(rf"\b{MONTH}(?=\d)", r"\1 ", text)
    text = re.sub(r"\b(before|after)(?=\d)", r"\1 ", text)
    return re.sub(rf"\b(\d{{1,2}})-(?={MONTH})", r"\1 ", text)


def mark_circa(text: str, source: Source) -> str:
    """Replace circa markers with "~" and drop copyright markers."""
    if source == "marc":
        # a publication year then a copyright year, "1985, c1983" or "2014, cop. 1991": the publication year
        text = re.sub(r"(\d{4}),\s*(?:c\.?|cop\.)\s?\d{4}", r"\1", text)
    # a circa word before a digit or a month name becomes "~"; a bare "c" before digits, or a lone "c"
    # before a word, is copyright in marc (dropped) and circa in axiell (becomes "~")
    text = re.sub(
        r"\b(c\.|ca\.|circa|circ(?:\.|\b)|approximately|about|approx(?:\.|\b))\s*(?=[\da-z])|\bc(?:\s?(?=\d)| (?=[a-z]))",
        lambda m: "~" if m.group(1) or source == "axiell" else "",
        text,
    )
    # a doubled marker, "ca. c. 1750", is still one circa
    return re.sub(r"~+", "~", text)


# --- stage 2: atoms -------------------------------------------------------------------------------
# Each atom reads a whole normalised string as one date expression, or returns None.


def atom(text: str) -> Span | None:
    if text.startswith("~"):
        return circa(atom(text[1:].strip()))
    for rule in (year, decade, century, season, month, day):
        try:
            span = rule(text)
        except ValueError:  # a date that does not exist: "29 february 1975", "nov 0000"
            return None
        if span and plausible(span[0].year):
            return span
    return None


def year(text: str) -> Span | None:
    """ "1984", "476"."""
    if re.fullmatch(r"\d{3,4}", text):
        return date(int(text), 1, 1), date(int(text), 12, 31)
    return None


def decade(text: str) -> Span | None:
    """ "1970s", "early 1970s", "mid-1970s", "early to mid 1970s"."""
    if m := re.fullmatch(
        rf"{QUALIFIED}(?!000)(\d{{3}})0s", text
    ):  # "0000s" is no decade
        return part(int(m[3]) * 10, 10, m[1], m[2])
    return None


def century(text: str) -> Span | None:
    """ "19th century", "19 cent.", "mid-19th century", "mid to late 19th century"."""
    if m := re.fullmatch(rf"{QUALIFIED}(\d{{1,2}}){ORD}? ?cent(?:ury|\.)?", text):
        return part((int(m[3]) - 1) * 100, 100, m[1], m[2])
    return None


def part(start: int, length: int, first: str | None, second: str | None) -> Span:
    """The early / mid / late part of a decade or century, or all of it; "mid to late" runs from
    mid's start to late's end. The 1st century starts in year 1."""
    lo, hi = PART[first or second][0], PART[second or first][1]
    return date(max(start + length * lo // 10, 1), 1, 1), date(
        start + length * hi // 10 - 1, 12, 31
    )


def season(text: str) -> Span | None:
    """ "winter 1962", which runs into 1963."""
    if m := re.fullmatch(rf"({'|'.join(SEASONS)}) {YEAR}", text):
        y, (first, last) = int(m[2]), SEASONS[m[1]]
        return date(y, first, 1), end_of_month(y + (last < first), last)
    return None


def month(text: str) -> Span | None:
    """ "nov 2007", "november 2007", "1887 nov"."""
    if m := re.fullmatch(rf"{MONTH} {YEAR}|{YEAR} {MONTH}", text):
        y, mo = int(m[2] or m[3]), MONTHS[m[1] or m[4]]
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


def single_day(year: int, month: int, day: int) -> Span:
    return date(year, month, day), date(year, month, day)


def end_of_month(year: int, month: int) -> date:
    return date(year, month, calendar.monthrange(year, month)[1])


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


# --- stage 3: ranges ------------------------------------------------------------------------------


def open_range(text: str) -> Span | None:
    """An atom with one side left open."""
    # open start: "to 1500", "early to 1800", "before 1800", "not after 1850", "-1953"
    if (m := re.fullmatch(rf"(?:(?:{QUAL} )?to|before|not after|-)\s?(.+)", text)) and (
        span := atom(m[2])
    ):
        return MIN, span[1]
    # "pre 1900", "post-1965": ten years before, or nine years after; before a decade or century,
    # "post 19th century", they simply mean before or after it
    if (m := re.fullmatch(r"(pre|post)[- ](.+)", text)) and (span := atom(m[2])):
        if span[0].year != span[1].year:
            if span[0] == MIN or span[1] == MAX:
                return None
            return (
                (MIN, span[0] - timedelta(days=1))
                if m[1] == "pre"
                else (span[1] + timedelta(days=1), MAX)
            )
        return widen(span, -10, 0) if m[1] == "pre" else widen(span, 0, 9)
    # open end: "after 1817", "not before 1804", "1994-", "1900-present"
    if (m := re.fullmatch(r"(?:after|not before) (.+)|(.+?)-(?:present)?", text)) and (
        span := atom(m[1] or m[2])
    ):
        return span[0], MAX
    return None


def closed_range(text: str) -> Span | None:
    """Two atoms joined, from the start of the left one to the end of the right one."""
    # "X-Y", "between X and Y", "X to Y", or "X/Y" when there is no hyphen
    m = re.fullmatch(r"(?:between )?(.+?)(?:-| and | to )(.+)|(.+?)/(.+)", text)
    if not m:
        return None
    left, right = (m[1] or m[3]).strip(" ."), (m[2] or m[4]).strip(" .")
    right = complete_short_year(left, right.replace("centuries", "century"))
    left = borrow_from_right(left, right)
    if left.endswith(right) and (span := atom(left)):
        return span  # "mid-1970s": the hyphen joined one expression, not two
    a, b = atom(left) or fallback(left), atom(right) or fallback(right)
    return (a[0], b[1]) if a and b else None


def complete_short_year(left: str, right: str) -> str:
    """ "1897-99", "1750-1" and "1970s-80s": a right side of one or two digits takes its leading
    digits from the left year."""
    if (m := re.fullmatch(r"(\d{1,2})s?", right)) and (year := re.search(YEAR, left)):
        return year[1][: 4 - len(m[1])] + right
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
        int(y) for y in re.findall(r"(?<![\d+])\d{4}(?!\d)", text) if plausible(int(y))
    ]
    if not years:
        return None
    return date(min(years), 1, 1), date(max(years), 12, 31)
