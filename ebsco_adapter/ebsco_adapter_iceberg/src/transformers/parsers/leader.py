class RawLeader:
    """
    leader is a fixed width field, properties are extracted from
    specific character ranges within the field value
    https://www.loc.gov/marc/bibliographic/bdleader.html
    """

    def __init__(self, field_value: str):
        self.field_value = field_value

    @property
    def type_of_record(self) -> str:
        """
        character 6 refers to the type of record
        >>> RawLeader("00000pam a22000003i 4500").type_of_record
        'a'

        Options present in EBSCO data are ['a', 'g']
        """

        return self.field_value[6]

    @property
    def bibliographic_level(self) -> str:
        """
        character 6 denotes the bibliographic level
        >>> RawLeader("00000cas a22000003  4500").bibliographic_level
        's'

        Options present in EBSCO data are ['m', 's']
        """
        return self.field_value[7]
