from datetime import datetime

from pymarc.record import Record

from .common import mandatory_field
from .parsers.date_from_005 import datetime_from_005


@mandatory_field("005", "last transaction time")
def extract_last_transaction_time(marc_record: Record) -> str:
    return marc_record["005"].format_field().strip()


def extract_last_transaction_time_to_datetime(marc_record: Record) -> datetime:
    last_transaction_time_str = extract_last_transaction_time(marc_record)
    return datetime_from_005(last_transaction_time_str)
