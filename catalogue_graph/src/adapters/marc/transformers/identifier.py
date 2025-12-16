from pymarc.record import Record
from .common import mandatory_field


@mandatory_field("001", "id")
def extract_id(marc_record: Record) -> str:
    return marc_record["001"].format_field().strip()
