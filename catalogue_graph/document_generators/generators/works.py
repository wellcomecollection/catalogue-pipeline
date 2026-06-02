from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    MergedWorkState,
    RedirectedMergedWork,
    VisibleMergedWork,
)
from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from models.pipeline.access_condition import (
    AccessCondition,
    AccessMethod,
    AccessStatus,
    AccessStatusRelationship,
)
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Contributor, Genre, Subject
from models.pipeline.format import Format
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Id, IdLabel, Language
from models.pipeline.identifier import Identified
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from models.pipeline.work_data import WorkData, WorkType
from models.pipeline.work_state import WorkRelations

from .identifiers import create_identified, create_source_identifier
from .locations import create_digital_location, create_physical_location
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


def create_item(
    locations: list | None = None,
    other_identifiers: list | None = None,
) -> Item:
    if locations is None:
        locations = [create_digital_location(location_type_id="iiif-image")]
    item_id = create_identified()
    return Item(id=item_id, locations=locations)


def create_digital_item(
    license_id: str = "cc-by",
    access_status: str | None = None,
) -> Item:
    access_conditions: list[AccessCondition] = []
    if access_status:
        access_conditions = [
            AccessCondition(
                method=AccessMethod(type="ViewOnline"),
                status=_make_access_status(access_status),
            )
        ]
    loc = create_digital_location(
        location_type_id="iiif-image",
        license_id=license_id,
        access_conditions=access_conditions,
    )
    return Item(id=create_identified(), locations=[loc])


def create_closed_stores_item() -> Item:
    loc = create_physical_location(location_type_id="closed-stores")
    return Item(id=create_identified(), locations=[loc])


def create_open_shelves_item() -> Item:
    loc = create_physical_location(location_type_id="open-shelves")
    return Item(id=create_identified(), locations=[loc])


def create_image_data() -> ImageData:
    return ImageData(
        id=create_identified(ontology_type="Image"),
        version=1,
        locations=[create_digital_location(location_type_id="iiif-image")],
    )


def create_note() -> Note:
    note_types = [
        IdLabel(id="general-note", label="Notes"),
        IdLabel(id="funding-information", label="Funding information"),
        IdLabel(id="location-of-duplicates-note", label="Location of duplicates"),
    ]
    return Note(
        note_type=rng.choice(note_types),
        contents=random_alphanumeric(),
    )


def _make_access_status(status_type: str) -> AccessStatus:
    if status_type == "LicensedResources.Resource":
        return AccessStatus(
            type="LicensedResources",
            relationship=AccessStatusRelationship(type="Resource"),
        )
    if status_type == "LicensedResources.RelatedResource":
        return AccessStatus(
            type="LicensedResources",
            relationship=AccessStatusRelationship(type="RelatedResource"),
        )
    return AccessStatus(type=status_type)
