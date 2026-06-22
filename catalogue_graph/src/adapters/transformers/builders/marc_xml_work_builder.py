from datetime import datetime

from pymarc.record import Record

from adapters.transformers.builders.source_work_builder import SourceWorkBuilder
from adapters.transformers.ebsco.parents import get_parents
from adapters.transformers.marc.alternative_titles import extract_alternative_titles
from adapters.transformers.marc.description import extract_description
from adapters.transformers.marc.identifier import extract_id
from adapters.transformers.marc.other_identifiers import extract_other_identifiers
from adapters.transformers.marc.title import extract_title
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Concept, Contributor, Genre, Subject
from models.pipeline.format import Format
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Language
from models.pipeline.identifier import SourceIdentifier, WorkSourceIdentifier
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from models.pipeline.source.work import SourceWork, SourceWorkState, VisibleSourceWork
from models.pipeline.work_data import WorkData, WorkType
from models.pipeline.work_state import WorkAncestor, WorkRelations


class MarcXmlWorkBuilder(SourceWorkBuilder):
    """
    Extends `SourceWorkBuilder` for MARC XML records.

    Extracts `source_id` from MARC 001 and provides default implementations for all
    work field properties, which can be overridden by adapter-specific subclasses.
    """

    def __init__(self, record: Record, last_modified: datetime) -> None:
        self.record = record
        super().__init__(self.source_identifier_value, last_modified)

    @property
    def source_identifier_value(self) -> str:
        identifier: str = extract_id(self.record)
        return identifier

    @property
    def predecessor_identifier(self) -> WorkSourceIdentifier | None:
        """Override in subclasses to link works to predecessors in another system (e.g. Folio → Sierra)."""
        return None

    @property
    def title(self) -> str:
        title: str = extract_title(self.record)
        return title

    @property
    def alternative_titles(self) -> list[str]:
        return extract_alternative_titles(self.record)

    @property
    def other_identifiers(self) -> list[SourceIdentifier]:
        return extract_other_identifiers(self.record)

    @property
    def format(self) -> Format | None:
        return None

    @property
    def description(self) -> str | None:
        return extract_description(self.record)

    @property
    def physical_description(self) -> str | None:
        return None

    @property
    def lettering(self) -> str | None:
        return None

    @property
    def created_date(self) -> Concept | None:
        return None

    @property
    def subjects(self) -> list[Subject]:
        return []

    @property
    def genres(self) -> list[Genre]:
        return []

    @property
    def contributors(self) -> list[Contributor]:
        return []

    @property
    def thumbnail(self) -> DigitalLocation | None:
        return None

    @property
    def production(self) -> list[ProductionEvent]:
        return []

    @property
    def languages(self) -> list[Language]:
        return []

    @property
    def edition(self) -> str | None:
        return None

    @property
    def notes(self) -> list[Note]:
        return []

    @property
    def duration(self) -> int | None:
        return None

    @property
    def items(self) -> list[Item]:
        return []

    @property
    def holdings(self) -> list[Holdings]:
        return []

    @property
    def collection_path(self) -> CollectionPath | None:
        return None

    @property
    def reference_number(self) -> str | None:
        return None

    @property
    def image_data(self) -> list[ImageData]:
        return []

    @property
    def work_type(self) -> WorkType:
        return "Standard"

    @property
    def current_frequency(self) -> str | None:
        return None

    @property
    def former_frequency(self) -> list[str]:
        return []

    @property
    def designation(self) -> list[str]:
        return []

    @property
    def ancestors(self) -> list[WorkAncestor]:
        return get_parents(self.record)

    @property
    def work_data(self) -> WorkData:
        return WorkData(
            title=self.title,
            alternative_titles=self.alternative_titles,
            other_identifiers=self.other_identifiers,
            format=self.format,
            description=self.description,
            physical_description=self.physical_description,
            lettering=self.lettering,
            created_date=self.created_date,
            subjects=self.subjects,
            genres=self.genres,
            contributors=self.contributors,
            thumbnail=self.thumbnail,
            production=self.production,
            languages=self.languages,
            edition=self.edition,
            notes=self.notes,
            duration=self.duration,
            items=self.items,
            holdings=self.holdings,
            collection_path=self.collection_path,
            reference_number=self.reference_number,
            image_data=self.image_data,
            work_type=self.work_type,
            current_frequency=self.current_frequency,
            former_frequency=self.former_frequency,
            designation=self.designation,
        )

    @property
    def work_state(self) -> SourceWorkState:
        """Extends the base work_state with predecessor identifier and ancestor relations from the MARC record."""
        return super().work_state.model_copy(
            update={
                "predecessor_identifier": self.predecessor_identifier,
                "relations": WorkRelations(ancestors=self.ancestors),
            }
        )

    def transform_visible_work(self) -> VisibleSourceWork:
        return VisibleSourceWork(
            version=self.version,
            state=self.work_state,
            data=self.work_data,
        )

    def transform_work(self) -> SourceWork:
        return self.transform_visible_work()
