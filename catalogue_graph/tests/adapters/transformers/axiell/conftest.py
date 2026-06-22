from pymarc.record import Field, Record, Subfield

# mypy: allow-untyped-calls


def make_axiell_record(identifier: str = "test001") -> Record:
    """Minimal valid Axiell MARC record with all required fields."""
    record = Record()
    record.add_field(Field(tag="001", data=identifier))
    record.add_field(
        Field(tag="245", subfields=[Subfield(code="a", value="Test Title")])
    )
    record.add_field(Field(tag="005", data="18530821094530.0"))
    record.add_field(
        Field(tag="035", subfields=[Subfield(code="a", value="(Calm RefNo)TestRefNo")])
    )
    record.add_field(Field(tag="351", subfields=[Subfield(code="c", value="Item")]))
    return record
