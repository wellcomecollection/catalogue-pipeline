"""
Functions for extracting data from the 008 control field
https://www.loc.gov/marc/bibliographic/bd008a.html
"""

import re
from datetime import date
from typing import NamedTuple

from pymarc.record import Record

from adapters.transformers.ebsco.parsers.positional_field import PositionalField
from adapters.transformers.marc.parsers.period import MAX, Span
from lookups import places


class RawField008(PositionalField):
    """
    008 is a fixed width field, properties are extracted from
    specific character ranges within the field value
    """

    control_field_code = "008"

    @property
    def languagecode(self) -> str:
        """
        characters 35-37 contain the language code
        >>> RawField008("900716s1991    maub    ob    001 0 lat  ").languagecode
        'lat'
        """
        return self.field_value[35:38]

    @property
    def placecode(self) -> str:
        """
        characters 15-17 refer to the place of publication, production, or execution
        >>> RawField008("800121d19791995acafr p o o   0    0engrc").placecode
        'aca'
        """
        return self.field_value[15:18]

    @property
    def date_1(self) -> str:
        """
        characters 7-10 represent "Date 1"
        >>> RawField008("800121d19791995acafr p o o   0    0engrc").date_1
        '1979'
        """
        return self.field_value[7:11]

    @property
    def date_2(self) -> str:
        """
        characters 11-14 represent "Date 2"
        >>> RawField008("800121d19791995acafr p o o   0    0engrc").date_2
        '1995'
        """
        return self.field_value[11:15]

    @property
    def date_type(self) -> str:
        """
        character 6 represents the Type of date/Publication status
        >>> RawField008("800121d19791995acafr p o o   0    0engrc").date_type
        'd'
        """
        return self.field_value[6]


class Field008:
    def __init__(self, field_content: str):
        self.raw_field = RawField008(field_content)

    @classmethod
    def from_record(cls, record: Record) -> "Field008 | None":
        raw = RawField008.from_record(record)
        return cls(raw.field_value) if raw else None

    @property
    def place_of_production(self) -> str | None:
        """
        Returns the full place name associated with the place code in characters 15-17
        >>> Field008("|||||||1979uuuustk").place_of_production
        'Scotland'
        >>> Field008("|||||||1979uuuuft ").place_of_production
        'Djibouti'

        Or None if the place cannot be resolved
        >>> Field008("|||||||1979uuuu|||").place_of_production
        """
        return places.from_code(self.raw_field.placecode)

    @property
    def dates(self) -> "Field008Dates | None":
        """The dates coded in characters 6-14."""
        date_type = self.raw_field.date_type
        date_1 = year_bounds(self.raw_field.date_1)
        if date_1 is None:
            return None
        # s single date, r reprint, t publication and copyright, p release and production: date 1 only
        if date_type in "srtp":
            return single(date_1)
        # c currently published, u status unknown: open-ended
        if date_type in "cu":
            return span(date_1.earliest, None)
        # d ceased publication, m multipart, q questionable: a range, open when date 2 is 9999 or unknown
        if date_type in "dmq":
            date_2 = (
                None
                if self.raw_field.date_2 == "9999"
                else year_bounds(self.raw_field.date_2)
            )
            return span(date_1.earliest, date_2 and date_2.latest)
        # e detailed date: date 2 holds the month and day
        month_day = self.raw_field.date_2
        if date_type == "e" and date_1.exact and month_day.isdigit():
            try:
                day = date(date_1.earliest, int(month_day[:2]), int(month_day[2:]))
            except ValueError:
                return None
            return Field008Dates(f"{day:%Y/%m/%d}", (day, day))
        return None


class Field008Dates(NamedTuple):
    label: str
    span: Span


class YearBounds(NamedTuple):
    earliest: int
    latest: int

    @property
    def exact(self) -> bool:
        return self.earliest == self.latest


def year_bounds(value: str) -> YearBounds | None:
    """The earliest and latest year a coded date can stand for: "192u" is 1920 to 1929."""
    is_valid = re.fullmatch(r"\d[\du]{3}", value)
    if not is_valid or not (earliest := int(value.replace("u", "0"))):
        return None
    return YearBounds(earliest, int(value.replace("u", "9")))


def single(bounds: YearBounds) -> Field008Dates:
    """One coded date: "1925", or "1920-1929" for "192u"."""
    label = (
        f"{bounds.earliest:04d}"
        if bounds.exact
        else f"{bounds.earliest:04d}-{bounds.latest:04d}"
    )
    return Field008Dates(
        label, (date(bounds.earliest, 1, 1), date(bounds.latest, 12, 31))
    )


def span(earliest: int, latest: int | None) -> Field008Dates:
    """A range between two coded dates: "1979-1995", or "1979-" when open-ended."""
    label = f"{earliest:04d}-" if latest is None else f"{earliest:04d}-{latest:04d}"
    end = MAX if latest is None else date(latest, 12, 31)
    return Field008Dates(label, (date(earliest, 1, 1), end))
