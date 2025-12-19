from datetime import datetime

import dateutil
from pymarc.record import Record

from adapters.ebsco.transformers.common import mandatory_field
from adapters.ebsco.transformers.contributors import extract_contributors
from adapters.ebsco.transformers.current_frequency import extract_current_frequency
from adapters.ebsco.transformers.description import extract_description
from adapters.ebsco.transformers.designation import extract_designation
from adapters.ebsco.transformers.edition import extract_edition
from adapters.ebsco.transformers.format import extract_format
from adapters.ebsco.transformers.genres import extract_genres
from adapters.ebsco.transformers.holdings import extract_holdings
from adapters.ebsco.transformers.language import extract_languages
from adapters.ebsco.transformers.other_identifiers import extract_other_identifiers
from adapters.ebsco.transformers.parents import get_parents
from adapters.ebsco.transformers.production import extract_production
from adapters.ebsco.transformers.subjects import extract_subjects
from adapters.marc.transformers.alternative_titles import extract_alternative_titles
from adapters.marc.transformers.title import extract_title
from models.pipeline.identifier import Id, SourceIdentifier
from models.pipeline.source.work import SourceWorkState, VisibleSourceWork
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkRelations
from utils.timezone import convert_datetime_to_utc_iso

EBSCO_IDENTIFIER_TYPE = Id(id="ebsco-alt-lookup")


def ebsco_source_identifier(id_value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=EBSCO_IDENTIFIER_TYPE, ontology_type="Work", value=id_value
    )


def ebsco_source_work_state(
    id_value: str, relations: WorkRelations | None = None
) -> SourceWorkState:
    current_time_iso: str = convert_datetime_to_utc_iso(datetime.now())

    return SourceWorkState(
        source_identifier=ebsco_source_identifier(id_value),
        # Using current time for both source_modified_time and modified_time
        # as we are not currently extracting a specific modified time from the record.
        source_modified_time=current_time_iso,
        modified_time=current_time_iso,
        relations=relations,
    )


def transform_record(marc_record: Record) -> VisibleSourceWork:
    work_id = extract_id(marc_record)
    work_data = WorkData(
        title=extract_title(marc_record),
        alternative_titles=extract_alternative_titles(marc_record),
        other_identifiers=extract_other_identifiers(marc_record),
        designation=extract_designation(marc_record),
        description=extract_description(marc_record),
        current_frequency=extract_current_frequency(marc_record),
        edition=extract_edition(marc_record),
        contributors=extract_contributors(marc_record),
        production=extract_production(marc_record),
        format=extract_format(marc_record),
        languages=extract_languages(marc_record),
        holdings=extract_holdings(marc_record),
        genres=extract_genres(marc_record),
        subjects=extract_subjects(marc_record),
    )

    relations = WorkRelations(ancestors=get_parents(marc_record))
    work_state = ebsco_source_work_state(work_id, relations)

    return VisibleSourceWork(
        # This version is a required field downstream, but we
        # do not create versions in the EBSCO adapter, so we
        # use a timestamp-based version, to ensure downstream
        # events are always seen as newer than prior ones.
        version=int(dateutil.parser.parse(work_state.source_modified_time).timestamp()),
        state=work_state,
        data=work_data,
    )


@mandatory_field("001", "id")
def extract_id(marc_record: Record) -> str:
    return marc_record["001"].format_field().strip()
