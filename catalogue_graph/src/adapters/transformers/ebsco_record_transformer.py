from adapters.transformers.ebsco.contributors import extract_contributors
from adapters.transformers.ebsco.current_frequency import extract_current_frequency
from adapters.transformers.ebsco.description import extract_description
from adapters.transformers.ebsco.designation import extract_designation
from adapters.transformers.ebsco.edition import extract_edition
from adapters.transformers.ebsco.format import extract_format
from adapters.transformers.ebsco.genres import extract_genres
from adapters.transformers.ebsco.holdings import extract_holdings
from adapters.transformers.ebsco.language import extract_languages
from adapters.transformers.ebsco.other_identifiers import extract_other_identifiers
from adapters.transformers.ebsco.production import extract_production
from adapters.transformers.ebsco.subjects import extract_subjects
from models.pipeline.concept import Contributor, Genre, Subject
from models.pipeline.format import Format
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Language
from models.pipeline.identifier import Id, SourceIdentifier
from models.pipeline.production import ProductionEvent

from .marcxml_record_transformer import MarcXMLRecordTransformer


class EbscoRecordTransformer(MarcXMLRecordTransformer):
    @property
    def source_identifier_type(self) -> Id:
        return Id(id="ebsco-alt-lookup")

    @property
    def other_identifiers(self) -> list[SourceIdentifier]:
        return extract_other_identifiers(self.record)

    @property
    def designation(self) -> list[str]:
        return extract_designation(self.record)

    @property
    def description(self) -> str | None:
        return extract_description(self.record)

    @property
    def current_frequency(self) -> str | None:
        return extract_current_frequency(self.record)

    @property
    def edition(self) -> str | None:
        return extract_edition(self.record)

    @property
    def contributors(self) -> list[Contributor]:
        return extract_contributors(self.record)

    @property
    def production(self) -> list[ProductionEvent]:
        return extract_production(self.record)

    @property
    def format(self) -> Format | None:
        return extract_format(self.record)

    @property
    def languages(self) -> list[Language]:
        return extract_languages(self.record)

    @property
    def holdings(self) -> list[Holdings]:
        return extract_holdings(self.record)

    @property
    def genres(self) -> list[Genre]:
        return extract_genres(self.record)

    @property
    def subjects(self) -> list[Subject]:
        return extract_subjects(self.record)
