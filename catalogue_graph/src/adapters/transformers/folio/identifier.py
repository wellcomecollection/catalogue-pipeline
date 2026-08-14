from pymarc.record import Record

from adapters.transformers.marc.common import mandatory_field


@mandatory_field("999", "instance uuid")
def extract_instance_uuid(marc_record: Record) -> str:
    """Extract the FOLIO instance UUID from MARC 999 $i."""
    for field in marc_record.get_fields("999"):
        if uuid := field.get_subfields("i"):
            return uuid[0]

    return ""


@mandatory_field("001", "hrid")
def extract_hrid(marc_record: Record) -> str:
    """Extract the FOLIO instance HRID from MARC 001."""
    return marc_record["001"].format_field().strip()
