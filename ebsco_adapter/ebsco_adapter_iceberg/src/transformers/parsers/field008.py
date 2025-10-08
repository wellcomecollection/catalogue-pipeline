"""
Functions for extracting data from the 008 control field
https://www.loc.gov/marc/bibliographic/bd008a.html
"""

from transformers.parsers.positional_field import PositionalField
from transformers.lookups import places


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
    def maximal_date_range(self) -> str | None:
        """
        date type "|" represents "no attempt to code", and "n" unknown, return None for either of these
        >>> Field008("|||||||1979uuuu").maximal_date_range
        >>> Field008("||||||n1979uuuu").maximal_date_range

        In a date, u represents an unknown digit.  In a "from" date, replace with 0
        >>> Field008("||||||u19uuuuuu").maximal_date_range
        '1900-'

        date type "u" represensts a continuing source where we don't know the end date.
        assume that it is ongoing.
        >>> Field008("||||||u1979uuuu").maximal_date_range
        '1979-'

        date type "c" represensts a continuing source where we know it is still being published
        >>> Field008("||||||u19799999").maximal_date_range
        '1979-'

        date type "s" represents a single year
        >>> Field008("||||||s1925uuuu").maximal_date_range
        '1925'

        If there is uncertainty in a single year, return a range from earliest to latest possible year.
        >>> Field008("||||||s192uuuuu").maximal_date_range
        '1920-1929'

        Date types r and t behave identically to s - although they are expected to contain two dates,
        we only care about the first one.
        >>> Field008("||||||r192u2009").maximal_date_range
        '1920-1929'
        >>> Field008("||||||t192u1956").maximal_date_range
        '1920-1929'

        Date types d and m both represent a range, and we want both dates
        >>> Field008("||||||d19252009").maximal_date_range
        '1925-2009'
        >>> Field008("||||||m19011956").maximal_date_range
        '1901-1956'

        Partial years are 0-filled in date 1 and 9-filled in date 2
        >>> Field008("||||||d19uu200u").maximal_date_range
        '1900-2009'
        >>> Field008("||||||m191u195u").maximal_date_range
        '1910-1959'
        """
        date_type = self.raw_field.date_type
        if date_type in 'n|':
            # Unknown or no attempt to code
            return None
        date_1 = self.raw_field.date_1
        if date_type in 'cu':
            # u=status unknown, so date_2 should be uuuu, but we don't care.
            # c=continuing, so date_2 should be 9999, but we don't care.
            return f"{date_1.replace('u', '0')}-"

        if date_type in 'srt':
            # s = single date,
            # rt = reprint/pub+copyright - both scenarios we are only interested in date 1
            # a single date
            if 'u' in date_1:
                return f"{date_1.replace('u', '0')}-{date_1.replace('u', '9')}"
            return date_1

        if date_type in "dm":
            # these two have subtly different meanings in MARC,
            # but for our purposes, they both represent a range.
            # d is an actual range of publication of a continuing resource
            # m is a pair of dates for a multipart item.
            date_2 = self.raw_field.date_2
            return f"{date_1.replace('u', '0')}-{date_2.replace('u', '9')}"

        # Otherwise, throw it.
        raise NotImplementedError(f"unexpected MARC008 date type value: {date_type}")
