from pymarc.record import Record


class RawField006:
    """
    006 is a fixed width field, properties are extracted from
    specific character ranges within the field value
    https://www.loc.gov/marc/bibliographic/bd006.html
    """

    def __init__(self, field_value: str):
        self.field_value = field_value

    def __bool__(self) -> bool:
        print(self.field_value)
        return bool(self.field_value)

    @staticmethod
    def from_record(record: Record) -> "RawField006 | None":
        if field := record.get("006"):
            return RawField006(field.value())
        return None

    @property
    def form_of_item(self) -> str:
        """
        Form of Item is character 6 in the 006 field
        >>> RawField006("m     o  d  |||||").form_of_item
        'o'
        """
        return self.field_value[6]
