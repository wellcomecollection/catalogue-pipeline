from pymarc.record import Record

from adapters.ebsco.models.work import SourceWork
from adapters.ebsco.transformers.alternative_titles import extract_alternative_titles
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
from adapters.ebsco.transformers.production import extract_production
from adapters.ebsco.transformers.subjects import extract_subjects
from adapters.ebsco.transformers.title import extract_title
from models.pipeline.identifier import Id, SourceIdentifier

EBSCO_IDENTIFIER_TYPE = Id(id="ebsco-alt-lookup")


def ebsco_source_identifier(id_value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=EBSCO_IDENTIFIER_TYPE, ontology_type="Work", value=id_value
    )


def transform_record(marc_record: Record) -> SourceWork:
    work_id = extract_id(marc_record)
    return SourceWork(
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
        source_identifier=SourceIdentifier(
            identifier_type=EBSCO_IDENTIFIER_TYPE,
            ontology_type="Work",
            value=work_id,
        ),
    )


@mandatory_field("001", "id")
def extract_id(marc_record: Record) -> str:
    return marc_record["001"].format_field().strip()
