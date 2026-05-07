from typing import Self

from pymarc.record import Record


class PositionalField:
    control_field_code: str

    def __init__(self, field_value: str):
        self.field_value = field_value

    def __bool__(self) -> bool:
        return bool(self.field_value)

    @classmethod
    def from_record(cls, record: Record) -> Self | None:
        if field := record.get(cls.control_field_code):
            return cls(field.value())
        return None
