from models.pipeline.source.work import InvisibleSourceWork, SourceWorkState
from utils.timezone import convert_datetime_to_utc_iso
from models.pipeline.identifier import Id, SourceIdentifier
from models.pipeline.work_data import WorkData
from pymarc.record import Record
from adapters.marc.transformers.identifier import extract_id
from adapters.marc.transformers.last_transaction_time import extract_last_transaction_time_to_datetime

import dateutil
from datetime import datetime
from ingestor.models.shared.invisible_reason import InvisibleReason
from adapters.marc.transformers.title import extract_title
from adapters.marc.transformers.alternative_titles import extract_alternative_titles

MIMSY_IDENTIFIER_TYPE = Id(id="mimsy-reference")


def transform_record(marc_record: Record) -> InvisibleSourceWork:
    work_id = extract_id(marc_record)
    work_data = WorkData(
        title=extract_title(marc_record),
        alternative_titles=extract_alternative_titles(marc_record),
    )

    work_state = axiell_source_work_state(work_id, extract_last_transaction_time_to_datetime(marc_record))

    return InvisibleSourceWork(
        version=int(dateutil.parser.parse(work_state.source_modified_time).timestamp()),
        state=work_state,
        data=work_data,
        invisibility_reasons=[
            InvisibleReason(type="MimsyWorksAreNotVisible")
        ]
    )


def axiell_source_work_state(
        id_value: str, source_modified_time: datetime | None = None
) -> SourceWorkState:
    current_time_iso: str = convert_datetime_to_utc_iso(datetime.now())
    source_modified_time_iso: str = convert_datetime_to_utc_iso(source_modified_time)
    return SourceWorkState(
        source_identifier=axiell_mimsy_source_identifier(id_value),
        source_modified_time=source_modified_time_iso,
        modified_time=current_time_iso
    )


def axiell_mimsy_source_identifier(id_value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=MIMSY_IDENTIFIER_TYPE, ontology_type="Work", value=id_value
    )
