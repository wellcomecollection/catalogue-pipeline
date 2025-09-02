from pymarc.record import Record

from models.work import SourceIdentifier, SourceWork
from transformers.alternative_titles import extract_alternative_titles
from transformers.common import mandatory_field
from transformers.title import extract_title


def ebsco_source_identifier(id_value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type="ebsco-alt-lookup", ontology_type="Work", value=id_value
    )


def transform(marc_record: Record) -> SourceWork:
    return SourceWork(
        id=extract_id(
            marc_record
        ),  # TODO: where should this become a SourceIdentifier?
        title=extract_title(marc_record),
        alternative_titles=extract_alternative_titles(marc_record),
    )


@mandatory_field("001", "id")
def extract_id(marc_record: Record) -> str:
    return marc_record["001"].format_field().strip()
