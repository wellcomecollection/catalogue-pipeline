from ingestor.extractors.works.base_works_extractor import (
    VisibleExtractedWork,
)
from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    MergedWorkState,
    RedirectedMergedWork,
    VisibleMergedWork,
)
from ingestor.models.neptune.node import WorkNode
from ingestor.models.neptune.query_result import (
    WorkHierarchy,
    WorkHierarchyItem,
)
from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from models.graph_node import Work
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Contributor, Genre, Subject
from models.pipeline.format import Format
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Id, Language
from models.pipeline.identifier import Identified
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from models.pipeline.work_data import WorkData, WorkType
from models.pipeline.work_state import WorkRelations

from .identifiers import create_source_identifier
from .random import random_alphanumeric, random_canonical_id, rng


def create_work_data(
    title: str | None = None,
    contributors: list[Contributor] | None = None,
    genres: list[Genre] | None = None,
    subjects: list[Subject] | None = None,
    languages: list[Language] | None = None,
    production: list[ProductionEvent] | None = None,
    items: list[Item] | None = None,
    format: Format | None = None,
    lettering: str | None = None,
    edition: str | None = None,
    duration: int | None = None,
    thumbnail: DigitalLocation | None = None,
    notes: list[Note] | None = None,
    image_data: list[ImageData] | None = None,
    holdings: list[Holdings] | None = None,
    collection_path: CollectionPath | None = None,
    work_type: WorkType = "Standard",
    former_frequency: list[str] | None = None,
    designation: list[str] | None = None,
    other_identifiers: list | None = None,
) -> WorkData:
    return WorkData(
        title=title or random_alphanumeric(15),
        contributors=contributors or [],
        genres=genres or [],
        subjects=subjects or [],
        languages=languages or [],
        production=production or [],
        items=items or [],
        format=format,
        lettering=lettering,
        edition=edition,
        duration=duration,
        thumbnail=thumbnail,
        notes=notes or [],
        image_data=image_data or [],
        holdings=holdings or [],
        collection_path=collection_path,
        work_type=work_type,
        former_frequency=former_frequency or [],
        designation=designation or [],
        other_identifiers=other_identifiers or [],
    )


def create_merged_work_state(
    availabilities: list[Id] | None = None,
) -> MergedWorkState:
    return MergedWorkState(
        source_identifier=create_source_identifier(),
        canonical_id=random_canonical_id(),
        merged_time="2025-01-01T00:00:00Z",
        source_modified_time="2025-01-01T00:00:00Z",
        availabilities=availabilities or [],
        merge_candidates=[],
        relations=WorkRelations(),
    )


def create_visible_merged_work(
    title: str | None = None,
    contributors: list[Contributor] | None = None,
    genres: list[Genre] | None = None,
    subjects: list[Subject] | None = None,
    languages: list[Language] | None = None,
    production: list[ProductionEvent] | None = None,
    items: list[Item] | None = None,
    format: Format | None = None,
    lettering: str | None = None,
    edition: str | None = None,
    duration: int | None = None,
    thumbnail: DigitalLocation | None = None,
    notes: list[Note] | None = None,
    image_data: list[ImageData] | None = None,
    holdings: list[Holdings] | None = None,
    collection_path: CollectionPath | None = None,
    work_type: WorkType = "Standard",
    former_frequency: list[str] | None = None,
    designation: list[str] | None = None,
    other_identifiers: list | None = None,
    availabilities: list[Id] | None = None,
) -> VisibleMergedWork:
    return VisibleMergedWork(
        version=1,
        data=create_work_data(
            title=title,
            contributors=contributors,
            genres=genres,
            subjects=subjects,
            languages=languages,
            production=production,
            items=items,
            format=format,
            lettering=lettering,
            edition=edition,
            duration=duration,
            thumbnail=thumbnail,
            notes=notes,
            image_data=image_data,
            holdings=holdings,
            collection_path=collection_path,
            work_type=work_type,
            former_frequency=former_frequency,
            designation=designation,
            other_identifiers=other_identifiers,
        ),
        state=create_merged_work_state(availabilities=availabilities),
    )


def create_invisible_merged_work() -> InvisibleMergedWork:
    return InvisibleMergedWork(
        version=1,
        data=create_work_data(),
        state=create_merged_work_state(),
        invisibility_reasons=[InvisibleReason(type="SourceFieldMissing", info="test")],
    )


def create_deleted_merged_work() -> DeletedMergedWork:
    return DeletedMergedWork(
        version=1,
        data=create_work_data(),
        state=create_merged_work_state(),
        deleted_reason=DeletedReason(type="DeletedFromSource", info="test"),
    )


def create_redirected_merged_work() -> RedirectedMergedWork:
    return RedirectedMergedWork(
        version=1,
        data=create_work_data(),
        redirect_target=Identified(
            canonical_id=random_canonical_id(),
            source_identifier=create_source_identifier(),
        ),
        state=create_merged_work_state(),
    )


def create_visible_extracted_work(
    ancestors: list[WorkHierarchyItem], merged_work: VisibleMergedWork | None = None
) -> VisibleExtractedWork:
    merged_work = merged_work or create_visible_merged_work()
    return VisibleExtractedWork(
        work=merged_work,
        hierarchy=WorkHierarchy(
            id=merged_work.state.canonical_id, ancestors=ancestors or []
        ),
        concepts=[],
    )


def create_work_hierarchy_item(parts: int | None = None) -> WorkHierarchyItem:
    work = create_visible_merged_work()
    work_node = Work(type="Work", id=work.state.canonical_id, label=work.data.title)
    return WorkHierarchyItem(
        work=WorkNode.model_validate(
            {
                "~id": "123",
                "~labels": ["Work"],
                "~properties": work_node,
            }
        ),
        parts=rng.randint(1, 5) if parts is None else parts,
    )
