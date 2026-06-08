import io

from pymarc import parse_xml_to_array
from pymarc.record import Record


def parse_single_marc_record(content: str) -> Record:
    """Parse MARC XML content and return exactly one Record.

    Raises a ValueError if the content does not contain exactly one record.
    """
    marc_records = parse_xml_to_array(io.StringIO(content))
    if len(marc_records) != 1:
        raise ValueError(f"Expected 1 record, got {len(marc_records)}")
    return marc_records[0]
