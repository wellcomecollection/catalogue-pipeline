from ingestor.extractors.images.images_extractor import ExtractedImage
from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.augmented.image import (
    AugmentedImage,
    AugmentedImageState,
    InferredData,
    ParentWork,
)
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from models.pipeline.identifier import Identified
from models.pipeline.image import ImageData
from models.pipeline.location import DigitalLocation

from .identifiers import create_identified, create_source_identifier
from .items import create_unidentifiable_item
from .locations import create_digital_location
from .random import random_canonical_id, rng
from .vectors import random_unit_length_vector
from .works import create_visible_merged_work, create_work_data


def random_hex_string() -> str:
    r, g, b = rng.randint(0, 255), rng.randint(0, 255), rng.randint(0, 255)
    return f"#{r:02X}{g:02X}{b:02X}"


def create_inferred_data() -> InferredData:
    return InferredData(
        features=random_unit_length_vector(4096),
        palette_embedding=random_unit_length_vector(1000),
        average_color_hex=random_hex_string(),
        aspect_ratio=rng.uniform(0.3, 2.0),
    )


def create_augmented_image(
    inferred_data: InferredData | None = None,
    parent_work: VisibleMergedWork | None = None,
    locations: list[DigitalLocation] | None = None,
) -> AugmentedImage:
    if parent_work:
        source = ParentWork(
            id=Identified(
                canonical_id=parent_work.state.canonical_id,
                source_identifier=parent_work.state.source_identifier,
                other_identifiers=parent_work.data.other_identifiers,
            ),
            data=parent_work.data,
            version=parent_work.version,
        )
    else:
        source = ParentWork(
            id=create_identified(),
            data=create_work_data(),
            version=1,
        )

    return AugmentedImage(
        state=AugmentedImageState(
            canonical_id=random_canonical_id(),
            source_identifier=create_source_identifier(
                ontology_type="Image", identifier_type_id="miro-image-number"
            ),
            inferred_data=inferred_data or create_inferred_data(),
        ),
        source=source,
        locations=locations or [create_digital_location(location_type_id="iiif-image")],
        version=1,
        modified_time="2001-01-01T01:01:01Z",
    )


def create_extracted_image(
    license_id: str = "cc-by",
    inferred_data: InferredData | None = None,
    parent_work: VisibleMergedWork | None = None,
) -> ExtractedImage:
    location = create_digital_location(
        location_type_id="iiif-image", license_id=license_id
    )

    parent_work = parent_work or create_visible_merged_work(
        items=[create_unidentifiable_item(locations=[location])]
    )
    image = create_augmented_image(
        parent_work=parent_work, inferred_data=inferred_data, locations=[location]
    )

    hierarchy = WorkHierarchy(
        id=parent_work.state.canonical_id, ancestors=[], children=[]
    )
    extracted_work = VisibleExtractedWork(
        work=parent_work, hierarchy=hierarchy, concepts=[]
    )
    return ExtractedImage(image=image, work=extracted_work)


def create_image_data() -> ImageData:
    return ImageData(
        id=create_identified(ontology_type="Image"),
        version=1,
        locations=[create_digital_location(location_type_id="iiif-image")],
    )
