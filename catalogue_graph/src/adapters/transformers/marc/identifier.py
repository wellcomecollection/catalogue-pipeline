from pymarc.record import Record

from .common import mandatory_field


@mandatory_field("001", "id")
def extract_id(marc_record: Record) -> str:
    return marc_record["001"].format_field().strip()


def has_id(marc_record: Record) -> bool:
    """Whether 001 is present and non-empty; id-less records are skipped upstream."""
    try:
        extract_id(marc_record)
    except ValueError:
        return False
    return True
