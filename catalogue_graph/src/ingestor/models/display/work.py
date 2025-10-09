from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.shared.serialisable import ElasticsearchModel
from ingestor.transformers.work_display_transformer import DisplayWorkTransformer
from utils.types import DisplayWorkType

from .concept import DisplayConcept, DisplayContributor, DisplayGenre, DisplaySubject
from .holdings import DisplayHoldings
from .id_label import DisplayId, DisplayIdLabel
from .identifier import DisplayIdentifier
from .item import DisplayItem
from .location import DisplayDigitalLocation
from .note import DisplayNote
from .production_event import DisplayProductionEvent
from .relation import DisplayRelation


class DisplayWork(ElasticsearchModel):
    id: str
    title: str | None
    alternative_titles: list[str]
    reference_number: str | None
    description: str | None
    physical_description: str | None
    work_type: DisplayIdLabel | None
    lettering: str | None
    created_date: DisplayConcept | None
    contributors: list[DisplayContributor]
    identifiers: list[DisplayIdentifier]
    subjects: list[DisplaySubject]
    genres: list[DisplayGenre]
    thumbnail: DisplayDigitalLocation | None
    items: list[DisplayItem]
    holdings: list[DisplayHoldings]
    availabilities: list[DisplayIdLabel]
    production: list[DisplayProductionEvent]
    languages: list[DisplayIdLabel]
    edition: str | None
    notes: list[DisplayNote]
    duration: int | None
    current_frequency: str | None
    former_frequency: list[str]
    designation: list[str]
    images: list[DisplayId]
    parts: list[DisplayRelation]
    part_of: list[DisplayRelation]
    type: DisplayWorkType

    @classmethod
    def from_extracted_work(cls, extracted: VisibleExtractedWork) -> "DisplayWork":
        work = extracted.work
        transformer = DisplayWorkTransformer(extracted)

        return DisplayWork(
            id=work.state.canonical_id,
            title=work.data.title,
            alternative_titles=work.data.alternative_titles,
            reference_number=work.data.reference_number,
            description=work.data.description,
            physical_description=work.data.physical_description,
            work_type=transformer.work_type,
            lettering=work.data.lettering,
            created_date=transformer.created_date,
            thumbnail=transformer.thumbnail,
            items=list(transformer.items),
            holdings=list(transformer.holdings),
            production=list(transformer.production),
            languages=list(transformer.languages),
            edition=work.data.edition,
            notes=list(transformer.notes),
            duration=work.data.duration,
            current_frequency=work.data.current_frequency,
            former_frequency=work.data.former_frequency,
            designation=work.data.designation,
            images=list(transformer.images),
            identifiers=list(transformer.identifiers),
            contributors=list(transformer.contributors),
            genres=list(transformer.genres),
            subjects=list(transformer.subjects),
            availabilities=transformer.availabilities,
            parts=list(transformer.parts),
            part_of=list(transformer.part_of),
            type=work.data.display_work_type,
        )
