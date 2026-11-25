import uuid

from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield


def extract_synced_folio_instance_id(record: Record) -> str | None:
    """The id of the FOLIO instance the Axiell to FOLIO sync writes for this record.

    Mirrors `_selection_state` and `_instance_id` in `steps/axiell_folio_sync/mapping`:
    only flagged, item-level records are synced, and the instance id is the GUID.
    """
    record_type = (first_non_empty_subfield("351", "c", record) or "").upper()
    if record_type != "ITEM" or not first_non_empty_subfield("980", "a", record):
        return None

    control_numbers = record.get_fields("001")
    try:
        return str(uuid.UUID((control_numbers[0].data or "").strip()))
    except (IndexError, ValueError):
        # The sync cannot write a record whose 001 is not a UUID.
        return None
