from pymarc.record import Field, Indicators, Record, Subfield

# mypy: allow-untyped-calls


def make_axiell_record(
    identifier: str = "test001",
    catalogue_status: str | None = "catalogued",
    ref_no: str | None = "TestRefNo",
    publish_to_web: str | None = "yes",
) -> Record:
    """Minimal valid Axiell MARC record with all required fields.

    Defaults to publish_to_web 'yes': the stylesheet emits the marker on every
    record and suppression fails closed without it.
    """
    record = Record()
    record.add_field(Field(tag="001", data=identifier))
    record.add_field(
        Field(tag="245", subfields=[Subfield(code="a", value="Test Title")])
    )
    record.add_field(Field(tag="005", data="18530821094530.0"))
    if ref_no is not None:
        record.add_field(
            Field(
                tag="035", subfields=[Subfield(code="a", value=f"(Calm RefNo){ref_no}")]
            )
        )
    record.add_field(Field(tag="351", subfields=[Subfield(code="c", value="Item")]))

    if catalogue_status is not None:
        record.add_field(
            Field(
                tag="583",
                indicators=Indicators("0", " "),
                subfields=[Subfield(code="l", value=catalogue_status)],
            )
        )
    if publish_to_web is not None:
        record.add_field(
            Field(
                tag="981",
                subfields=[Subfield(code="a", value=publish_to_web)],
            )
        )
    return record
