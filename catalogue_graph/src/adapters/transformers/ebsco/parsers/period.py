import re
from datetime import datetime

from models.pipeline.concept import DateTimeRange, Period
from models.pipeline.identifier import Identifiable, Unidentifiable

# Explicitly discard SGML escape sequences and doubly-escaped sequences.
# This prevents the RE_KEEP sequence misinterpreting &#40; (lparen)
# or &amp;#41; (rparen) as the numbers 40 and 41 respectively.
RE_DISCARD = re.compile(r"(&(amp;)?.+?;)")

# A digit run immediately followed by just enough dashes to pad it to a
# 4-character year is a MARC convention for an unknown trailing digit, e.g.
# "182-" (the 1820s), "19--" (the 1900s). The specific 3+1/2+2/1+3
# alternatives must come before the general `\d{2,4}` fallback, otherwise a
# *complete* 4-digit year followed by a real range-separator dash (e.g.
# "1956-", meaning "1956 onwards") would be mistaken for a partial year.
RE_KEEP = re.compile(r"\d{3}-(?!-)|\d{2}-{2}(?!-)|\d-{3}(?!-)|\d{2,4}|-")

# Matches a partial year: 1-3 digits padded to 4 characters with dashes.
RE_PARTIAL_YEAR = re.compile(r"^\d{1,3}-{1,3}$")

# matcher for "nth century" with any single trailing punctuation
# actually matches any character at all directly after century, but
# it's most likely to be a comma or dot, and it is more permissive if
# we allow anything there.
RE_NTH_CENTURY = re.compile(r"(\d+)\w{2} century.?")

# Matcher for date ranges consisting of four-digit years.
# The scala transformer does not create ranges for subdivisions
# consisting of years with fewer than 4 digits (e.g. 500-1400 from ebs1211100e)
RE_4_DIGIT_DATE_RANGE = re.compile(r"\d{4}(-(\d{4})?)?")


def parse_period(
    period: str, identifier: Identifiable | Unidentifiable | None = None
) -> Period:
    """
    Converts a string representation of a period into a Period,
    giving a concrete date/time range.
    >>> parse_period("1988-1990")
    Period(id=Unidentifiable(canonical_id=None, type='Unidentifiable'), label='1988-1990', type='Period', range=DateTimeRange(from_time='1988-01-01T00:00:00Z', to_time='1990-12-31T23:59:59.999999999Z', label='1988-1990'))

    Messy/partial years are recovered where possible.
    >>> parse_period("1825-[19--?]").range
    DateTimeRange(from_time='1825-01-01T00:00:00Z', to_time='1999-12-31T23:59:59.999999999Z', label='1825-[19--?]')

    Anything else degrades to a label-only Period rather than raising.
    >>> parse_period("mid century")
    Period(id=Unidentifiable(canonical_id=None, type='Unidentifiable'), label='mid century', type='Period', range=None)
    """
    range_fn = century_to_range if "century" in period else to_range
    try:
        date_range = range_fn(period)
    except ValueError:
        date_range = None
    return Period(
        label=period,
        range=date_range,
        type="Period",
        id=identifier if identifier else Unidentifiable(),
    )


def century_to_range(period: str) -> DateTimeRange:
    """
    Returns a range for a period representing a century, expressed as "nth century"
    >>> r = century_to_range("19th century")
    >>> r.from_time
    '1800-01-01T00:00:00Z'
    >>> r.to_time
    '1899-12-31T23:59:59.999999999Z'

    It is sensitive to all the English ordinal suffixes
    >>> r = century_to_range("21st century")
    >>> r.from_time
    '2000-01-01T00:00:00Z'
    >>> r = century_to_range("22nd century")
    >>> r.from_time
    '2100-01-01T00:00:00Z'
    >>> r = century_to_range("23rd century")
    >>> r.from_time
    '2200-01-01T00:00:00Z'
    """
    match = RE_NTH_CENTURY.match(period)
    if not match:
        raise ValueError(f"Invalid century format: {period}")
    century_ordinal = int(match.group(1))
    century_prefix = century_ordinal - 1
    return DateTimeRange.model_validate(
        {
            "label": period,
            "from": start_of_year(f"{century_prefix}00"),
            "to": end_of_year(f"{century_prefix}99"),
        }
    )


def to_range(period: str) -> DateTimeRange:
    """
    Interpret a period string as a range.

    A range is from the start of the first year to the end of the last year
    >>> r = to_range("[2016]-[2020]")
    >>> r.from_time
    '2016-01-01T00:00:00Z'
    >>> r.to_time
    '2020-12-31T23:59:59.999999999Z'

    Although not necessarily part of a true range definition, copyright dates are interpreted as part of the range
    >>> r = to_range("1986 printing, c1977.")
    >>> r.from_time[:4]
    '1977'
    >>> r.to_time[:4]
    '1986'

    A trailing dash on a partial year is a MARC convention for an unknown
    trailing digit - "182-" means somewhere in the 1820s.
    >>> r = to_range("182-")
    >>> r.from_time
    '1820-01-01T00:00:00Z'
    >>> r.to_time
    '1829-12-31T23:59:59.999999999Z'

    An open lower bound.
    >>> r = to_range("- 1792")
    >>> r.to_time
    '1792-12-31T23:59:59.999999999Z'

    An abbreviated end year.
    >>> r = to_range("1742 - 44")
    >>> r.from_time
    '1742-01-01T00:00:00Z'
    >>> r.to_time
    '1744-12-31T23:59:59.999999999Z'
    """
    from_part, to_part = crack(preprocess(period))
    return DateTimeRange.model_validate(
        {"label": period, "from": start_of_year(from_part), "to": end_of_year(to_part)}
    )


def preprocess(period: str) -> str:
    """
    Preprocessing removes various non-meaningful strings from the period string.

    The only content in a date subfield (260/264 subfield c or g) from EBSCO
    is the year(s) in question, and hyphens (indicating from/to/between)

    There are no more precise dates than years in the EBSCO dataset, so
    no months need to be considered.

    Examples of cruft that needs to be removed before processing include:
     - the word "printing"
    >>> preprocess("1986 printing, c1977.")
    '1986 1977'

     - Square brackets
    >>> preprocess("[2016]-[2020]")
    '2016 - 2020'

     - Escaped angle brackets
    >>> preprocess("1961-&lt;2024&gt;")
    '1961 - 2024'

    - A doubly escaped right square bracket for some reason?
    >>> preprocess('[1960?-&amp;#x5d;')
    '1960 -'

    Contrary to CALM data, where the 'c' prefix is to be retained
    and interpreted as "circa", here it is in free variation with
    the copyright symbol © See https://wellcomecollection.org/works/fudnajnb
    Currently, copyright contributes to the range in 260/264 statements, but not in 008
    This inconsistency may be fixed at some point in the future.

    >>> preprocess("©1981-[c1987];")
    '1981 - 1987'

    There are also some entries that consist of more than two years
    >>> preprocess("©1988-©2001/2002.")
    '1988 - 2001 2002'

    A year might only be two digits, with the century being implied
    >>> preprocess("1961-72 [v. 3, c1973]")
    '1961 - 72 1973'

    A trailing "--" is kept attached to its digits as a single unknown-digit
    token, rather than being split into separate hyphens by the range
    separator logic.
    >>> preprocess("1825-[19--?]")
    '1825 - 19--'
    """
    return " ".join(RE_KEEP.findall(RE_DISCARD.sub("", period)))


def crack(range_string: str) -> tuple[str, str]:
    """
    Crack a (possible) range string into parts representing its start and end.
    This function assumes the range string has been preprocessed
    - it should only consist of dates (years) and hyphens

    A range starting with a hyphen represents any time up to the given date.
    >>> crack("- 1812")
    ('', '1812')

    A range ending with a hyphen represents any time from the given date.
    >>> crack("1812 -")
    ('1812', '')

    A range that consists of a single date returns that date as both bounds
    >>> crack("1812")
    ('1812', '1812')

    A "range" may have been expressed just as a list of date instances, not hyphenated
    >>> crack("1812 1999")
    ('1812', '1999')

    The list of dates may not be in order, there may be more than two
    >>> crack("1812 1999 1982")
    ('1812', '1999')

    The "to" date may be abbreviated, the century being inherited from the "from" date.
    >>> crack("1961 - 72")
    ('1961', '1972')

    >>> crack("1961 - 5")
    ('1961', '1965')

    It is also possible that the to part consists of multiple years, not all of them being complete
    >>> crack("1961 - 72 1973")
    ('1961', '1973')

    A partial year with no explicit end is its own decade/century - both
    bounds resolve from the same placeholder (see start_of_year/end_of_year).
    >>> crack("182-")
    ('182-', '182-')

    A partial year can appear as the "to" part of a range too.
    >>> crack("1825 - 19--")
    ('1825', '19--')

    Non-year content (e.g. roman numerals) can leave a meaningless standalone
    hyphen behind; it's discarded in favour of the real separator.
    >>> crack("- 1788 - 1789")
    ('1788', '1789')

    A trailing hyphen left the same way isn't an abbreviated end year either.
    >>> crack("1794 - -")
    ('1794', '')
    """
    # Split on whitespace rather than partitioning on "-" directly: a
    # partial year like "19--" has its dashes glued to its digits, so only a
    # standalone "-" (its own space-separated part) is a real separator.
    parts = range_string.split(" ")

    if "-" in parts:
        # A leading "-" with nothing before it is either an open lower bound
        # ("- 1812") or a leftover from discarded non-year content preceding
        # the real range - only the latter if another "-" remains to split on.
        while parts[0] == "-" and parts.count("-") > 1:
            parts = parts[1:]

        separator = parts.index("-")
        from_parts = parts[:separator]
        # A bare "-" here is noise, not a digit to fill in as an end year.
        to_parts = [part for part in parts[separator + 1 :] if part != "-"]
        from_part = min(from_parts) if from_parts else ""
        # Fill in any implied missing numbers in the to part.
        to_part = (
            max(fill_year_prefix(from_part, to_year) for to_year in to_parts)
            if from_part and to_parts
            else max(to_parts, default="")
        )
    elif len(parts) == 1:
        # it's just one date (or a lone partial year, standing for its own range)
        from_part = to_part = parts[0]
    else:
        from_part, to_part = min(parts), max(parts)

    return from_part, to_part


def fill_year_prefix(from_year: str, to_year: str) -> str:
    """
    A common abbreviated way to express a range of years is to assume that the century
    digits of the "from" year also apply to the "to" year, and only provide the differing
    digits, e.g. 1964-72 or 650-55

    If the second year is fully defined, it is returned as-is
    >>> fill_year_prefix("1964", "2072")
    '2072'

    >>> fill_year_prefix("1964", "72")
    '1972'

    >>> fill_year_prefix("650", "55")
    '655'

    Much less common, but still handled by this function, the decade or millennium may be inherited
    >>> fill_year_prefix("2004", "9")
    '2009'

    >>> fill_year_prefix("1790", "810")
    '1810'
    """
    if len(to_year) < len(from_year):
        to_year = from_year[: -len(to_year)] + to_year
    return to_year


def resolve_partial_year(year: str, fill: str) -> str:
    """
    Fill a partial year's unknown trailing digits with `fill` - "0" resolves
    the start of its decade/century/millennium, "9" the end.
    >>> resolve_partial_year("182-", "0")
    '1820'
    >>> resolve_partial_year("182-", "9")
    '1829'
    >>> resolve_partial_year("19--", "9")
    '1999'

    A complete year is returned unchanged.
    >>> resolve_partial_year("1980", "9")
    '1980'
    """
    if RE_PARTIAL_YEAR.fullmatch(year):
        return year.replace("-", fill)
    return year


def start_of_year(year: str) -> str:
    year = resolve_partial_year(year, "0")
    return (datetime(int(year), 1, 1) if year else datetime.min).isoformat() + "Z"


def end_of_year(year: str) -> str:
    year = resolve_partial_year(year, "9")
    return (
        (
            datetime(int(year), 12, 31, 23, 59, 59, 1000000 - 1)
            if year
            else datetime.max
        ).isoformat()
        + "999Z"
    )  # hack - EoY in the scala transformer has nanosecond precision
