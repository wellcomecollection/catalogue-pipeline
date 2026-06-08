from __future__ import annotations

from datetime import datetime

from pymarc.record import Record

from adapters.transformers.ebsco_record_transformer import EbscoRecordTransformer
from models.pipeline.source.work import VisibleSourceWork

DEFAULT_SOURCE_MODIFIED_TIME = datetime(2020, 1, 1)


def transform_ebsco_record(
    marc_record: Record,
    *,
    source_modified_time: datetime = DEFAULT_SOURCE_MODIFIED_TIME,
) -> VisibleSourceWork:
    """Convenience helper for tests."""
    return EbscoRecordTransformer(
        marc_record, last_modified=source_modified_time
    ).visible_work
