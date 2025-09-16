from pymarc.record import Record
from transformers.parsers import field006, leader

from models.work import Format, EJournals, EBooks

BIBLIOGRAPHIC_LEVELS = {
    "m": EBooks,
    "s": EJournals
}


def extract_format(record: Record) -> Format | None:
    raw_leader = leader.RawLeader(record.leader)
    raw_006 = field006.RawField006.from_record(record)
    if raw_006 and raw_006.form_of_item == "o" and raw_leader.type_of_record == "a":
        return BIBLIOGRAPHIC_LEVELS.get(
            raw_leader.bibliographic_level,
            None
        )
    return None
