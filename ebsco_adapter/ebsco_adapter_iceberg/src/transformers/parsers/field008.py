"""
Functions for extracting data from the 008 control field
https://www.loc.gov/marc/bibliographic/bd008a.html
"""


# TODO: This is not plumbed in, I want to investigate whether
#    it is needed at all for MARC data before I carry on.
#    It is needed if there exists one of:
#       * A record with no 260/264
#       * A record whose first 260/264 lacks a date
#   I'm also a little uncertain as to whether the logic in the Scala
#   correct.

class RawField008:
    """
    008 is a fixed width field, properties are extracted from
    specific character ranges within the field value
    """

    def __init__(self, field_value: str):
        self.field_value = field_value

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
