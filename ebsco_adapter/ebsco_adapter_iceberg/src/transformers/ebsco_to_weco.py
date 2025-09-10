from pymarc.record import Record

from models.work import SourceIdentifier, SourceWork
from transformers.alternative_titles import extract_alternative_titles
from transformers.common import mandatory_field
from transformers.contributors import extract_contributors
from transformers.current_frequency import extract_current_frequency
from transformers.description import extract_description
from transformers.designation import extract_designation
from transformers.edition import extract_edition
from transformers.other_identifiers import extract_other_identifiers
from transformers.title import extract_title


def ebsco_source_identifier(id_value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type="ebsco-alt-lookup", ontology_type="Work", value=id_value
    )


def transform_record(marc_record: Record) -> SourceWork:
    work_id = extract_id(marc_record)
    return SourceWork(
        id=work_id,
        title=extract_title(marc_record),
        alternative_titles=extract_alternative_titles(marc_record),
        other_identifiers=extract_other_identifiers(marc_record),
        designation=extract_designation(marc_record),
        description=extract_description(marc_record),
        current_frequency=extract_current_frequency(marc_record),
        edition=extract_edition(marc_record),
        contributors=extract_contributors(marc_record),
    )


@mandatory_field("001", "id")
def extract_id(marc_record: Record) -> str:
    return marc_record["001"].format_field().strip()
