"""
Creates example image documents for catalogue API tests using deterministic random data via a seeded RNG.

Run from the catalogue_graph directory:
    uv run python -m document_generators.create_test_image_documents
"""

import json
import random
import string
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from freezegun import freeze_time

from ingestor.extractors.images.images_extractor import ExtractedImage
from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.augmented.image import (
    AugmentedImage,
    AugmentedImageState,
    InferredData,
    ParentWork,
)
from ingestor.models.indexable.image import IndexableImage
from ingestor.models.merged.work import MergedWorkState, VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from models.pipeline.concept import (
    Concept,
    Contributor,
    DateTimeRange,
    Genre,
    Period,
    Subject,
)
from models.pipeline.id_label import Id, Language
from models.pipeline.identifier import Identified, SourceIdentifier, Unidentifiable
from models.pipeline.location import DigitalLocation, LocationType
from models.pipeline.production import ProductionEvent
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkRelations

TEST_DOCUMENTS_DIR = Path(__file__).resolve().parent / "test_documents"

# Seeded RNG for deterministic output
rng = random.Random(0)


def random_alphanumeric(length: int) -> str:
    chars = string.ascii_letters + string.digits
    return "".join(rng.choice(chars) for _ in range(length))


def random_canonical_id() -> str:
    alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "".join(rng.choice(alphabet) for _ in range(8))


def create_source_identifier(
    ontology_type: str = "Work",
    identifier_type_id: str = "sierra-system-number",
) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=Id(id=identifier_type_id),
        ontology_type=ontology_type,
        value=random_alphanumeric(10),
    )


def create_identified(
    canonical_id: str | None = None, ontology_type: str = "Work"
) -> Identified:
    return Identified(
        canonical_id=canonical_id or random_canonical_id(),
        source_identifier=create_source_identifier(ontology_type=ontology_type),
    )


def create_digital_location(
    license_id: str = "cc-by",
    location_type_id: str = "iiif-image",
) -> DigitalLocation:
    url_id = random_alphanumeric(3)
    link_text = f"Link text: {random_alphanumeric(7)}"
    return DigitalLocation(
        url=f"https://iiif.wellcomecollection.org/image/{url_id}.jpg/info.json",
        location_type=LocationType(id=location_type_id),
        license=Id(id=license_id),
        link_text=link_text,
        access_conditions=[],
    )


def create_inferred_data() -> InferredData:
    features = [rng.uniform(-0.05, 0.05) for _ in range(4096)]
    palette_embedding = [rng.uniform(-0.05, 0.05) for _ in range(1000)]
    r, g, b = rng.randint(0, 255), rng.randint(0, 255), rng.randint(0, 255)
    color_hex = f"#{r:02X}{g:02X}{b:02X}"
    aspect_ratio = rng.uniform(0.3, 2.0)
    return InferredData(
        features=features,
        palette_embedding=palette_embedding,
        average_color_hex=color_hex,
        aspect_ratio=aspect_ratio,
    )


def create_work_data(
    title: str | None = None,
    contributors: list[Contributor] | None = None,
    genres: list[Genre] | None = None,
    subjects: list[Subject] | None = None,
    languages: list[Language] | None = None,
    production: list[ProductionEvent] | None = None,
) -> WorkData:
    return WorkData(
        title=title or random_alphanumeric(15),
        contributors=contributors or [],
        genres=genres or [],
        subjects=subjects or [],
        languages=languages or [],
        production=production or [],
    )


def create_merged_work_state() -> MergedWorkState:
    return MergedWorkState(
        source_identifier=create_source_identifier(),
        canonical_id=random_canonical_id(),
        merged_time="2025-01-01T00:00:00Z",
        source_modified_time="2025-01-01T00:00:00Z",
        availabilities=[],
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
        ),
        state=create_merged_work_state(),
    )


def create_augmented_image(
    license_id: str = "cc-by",
    inferred_data: InferredData | None = None,
    parent_work_data: WorkData | None = None,
) -> AugmentedImage:
    canonical_id = random_canonical_id()
    return AugmentedImage(
        state=AugmentedImageState(
            canonical_id=canonical_id,
            source_identifier=create_source_identifier(
                ontology_type="Image", identifier_type_id="miro-image-number"
            ),
            inferred_data=inferred_data or create_inferred_data(),
        ),
        source=ParentWork(
            id=create_identified(),
            data=parent_work_data or create_work_data(),
            version=1,
        ),
        locations=[create_digital_location(license_id=license_id)],
        version=1,
        modified_time="2001-01-01T01:01:01Z",
    )


def create_extracted_image(
    license_id: str = "cc-by",
    inferred_data: InferredData | None = None,
    parent_work: VisibleMergedWork | None = None,
    parent_work_data: WorkData | None = None,
) -> ExtractedImage:
    image = create_augmented_image(
        license_id=license_id,
        inferred_data=inferred_data,
        parent_work_data=parent_work_data
        or (parent_work.data if parent_work else None),
    )
    work = parent_work or create_visible_merged_work()
    hierarchy = WorkHierarchy(id=work.state.canonical_id, ancestors=[], children=[])
    extracted_work = VisibleExtractedWork(work=work, hierarchy=hierarchy, concepts=[])
    return ExtractedImage(image=image, work=extracted_work)


def create_genre(label: str, concepts: list[Concept] | None = None) -> Genre:
    if concepts is None:
        concepts = [
            Concept(
                id=create_identified(ontology_type="Genre"),
                label=random_alphanumeric(15),
                type="GenreConcept",
            ),
            Concept(
                id=create_identified(ontology_type="Concept"),
                label=random_alphanumeric(15),
                type="Concept",
            ),
            Concept(
                id=create_identified(ontology_type="Concept"),
                label=random_alphanumeric(15),
                type="Concept",
            ),
        ]
    return Genre(label=label, concepts=concepts)


def create_genre_concept(canonical_id: str) -> Concept:
    return Concept(
        id=Identified(
            canonical_id=canonical_id,
            source_identifier=create_source_identifier(ontology_type="Genre"),
        ),
        label=random_alphanumeric(15),
        type="GenreConcept",
    )


def create_concept(canonical_id: str) -> Concept:
    return Concept(
        id=Identified(
            canonical_id=canonical_id,
            source_identifier=create_source_identifier(ontology_type="Concept"),
        ),
        label=random_alphanumeric(15),
        type="Concept",
    )


def create_person_contributor(label: str) -> Contributor:
    return Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label=label, type="Person"),
        roles=[],
        primary=True,
    )


def create_period_for_year(year: str) -> Period:
    return Period(
        id=Unidentifiable(),
        label=year,
        range=DateTimeRange(
            **{
                "from": f"{year}-01-01T00:00:00Z",
                "to": f"{year}-12-31T23:59:59Z",
                "label": year,
            }
        ),
    )


@freeze_time("2001-01-01T01:01:01Z")
def transform_image(extracted: ExtractedImage) -> dict[str, Any]:
    indexable = IndexableImage.from_extracted_image(extracted)
    return indexable.model_dump(mode="json", exclude_none=True)


def save_document(
    doc_id: str, description: str, image_id: str, document: dict[str, Any]
) -> None:
    TEST_DOCUMENTS_DIR.mkdir(parents=True, exist_ok=True)
    path = TEST_DOCUMENTS_DIR / f"{doc_id}.json"

    output = {
        "description": description,
        "id": image_id,
        "document": document,
    }

    # Only write if content has changed (ignore createdAt)
    if path.exists():
        existing = json.loads(path.read_text())
        existing.pop("createdAt", None)
        if existing == output:
            return

    output["createdAt"] = datetime.now(UTC).isoformat()
    path.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n")


def save_images(images: list[ExtractedImage], description: str, doc_id: str) -> None:
    if len(images) == 1:
        document = transform_image(images[0])
        image_id = images[0].image.state.canonical_id
        save_document(doc_id, description, image_id, document)
    else:
        for index, image in enumerate(images):
            document = transform_image(image)
            image_id = image.image.state.canonical_id
            save_document(f"{doc_id}.{index}", description, image_id, document)


def save_image(image: ExtractedImage, description: str, doc_id: str) -> None:
    save_images([image], description, doc_id)


# ---------- Test document generators ----------


def create_images_with_different_licenses() -> None:
    cc_by_images = [create_extracted_image(license_id="cc-by") for _ in range(5)]
    pdm_images = [create_extracted_image(license_id="pdm") for _ in range(2)]
    save_images(
        cc_by_images + pdm_images,
        description="images with different licenses",
        doc_id="images.different-licenses",
    )


def create_images_with_different_contributors() -> None:
    carrots = Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label="carrots", type="Agent"),
        roles=[],
        primary=True,
    )
    parrots = Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label="parrots", type="Organisation"),
        roles=[],
        primary=True,
    )
    parrots_meeting = Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label="parrots", type="Meeting"),
        roles=[],
        primary=True,
    )
    images = [
        create_extracted_image(
            parent_work=create_visible_merged_work(contributors=[carrots])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(contributors=[carrots, parrots])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(
                contributors=[carrots, parrots_meeting]
            )
        ),
    ]
    save_images(
        images,
        description="images with different contributors",
        doc_id="images.contributors",
    )


def create_images_with_different_genres() -> None:
    carrot_counselling = create_genre("Carrot counselling")
    dodo_divination = create_genre("Dodo divination")
    emu_entrepreneurship = create_genre("Emu entrepreneurship")
    falcon_finances = create_genre("Falcon finances")

    images = [
        create_extracted_image(
            parent_work=create_visible_merged_work(genres=[carrot_counselling])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(genres=[dodo_divination])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(
                genres=[emu_entrepreneurship, falcon_finances, carrot_counselling]
            )
        ),
    ]
    save_images(
        images,
        description="images with different genres",
        doc_id="images.genres",
    )


def create_image_without_inferred_data() -> None:
    empty_inferred = InferredData(
        features=[], palette_embedding=[], average_color_hex=None, aspect_ratio=None
    )
    image = create_extracted_image(inferred_data=empty_inferred)
    save_image(
        image,
        description="an image without any inferred data",
        doc_id="images.inferred-data.none",
    )


def create_contributor_filter_test_examples() -> None:
    machiavelli = Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label="Machiavelli, Niccolo", type="Person"),
        roles=[],
        primary=True,
    )
    hypatia = Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label="Hypatia", type="Person"),
        roles=[],
        primary=True,
    )
    said = Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label="Edward Said", type="Person"),
        roles=[],
        primary=True,
    )

    images = [
        create_extracted_image(
            parent_work=create_visible_merged_work(contributors=[machiavelli])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(contributors=[said])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(contributors=[hypatia])
        ),
    ]
    save_images(
        images,
        description="examples for the contributor filter tests",
        doc_id="images.examples.contributor-filter-tests",
    )


def create_genre_filter_test_examples() -> None:
    carrot_counselling = create_genre(
        "Carrot counselling",
        concepts=[
            create_genre_concept("g00dcafe"),
            create_concept("baadf00d"),
        ],
    )
    dodo_divination = create_genre("Dodo divination")
    emu_entrepreneurship = create_genre(
        "Emu entrepreneurship",
        concepts=[create_genre_concept("g00dcafe")],
    )
    falcon_finances = create_genre(
        "Falcon finances",
        concepts=[create_genre_concept("baadf00d")],
    )

    images = [
        create_extracted_image(
            parent_work=create_visible_merged_work(genres=[carrot_counselling])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(genres=[dodo_divination])
        ),
        create_extracted_image(
            parent_work=create_visible_merged_work(
                genres=[emu_entrepreneurship, falcon_finances]
            )
        ),
    ]
    save_images(
        images,
        description="examples for the genre filter tests",
        doc_id="images.examples.genre-filter-tests",
    )


def create_image_with_every_include() -> None:
    work = create_visible_merged_work(
        title="Apple agitator",
        languages=[
            Language(id="eng", label="English"),
            Language(id="tur", label="Turkish"),
        ],
        contributors=[
            create_person_contributor("Adrian Aardvark"),
            create_person_contributor("Beatrice Buffalo"),
        ],
        genres=[
            create_genre("Crumbly cabbages"),
            create_genre("Deadly durians"),
        ],
    )
    image = create_extracted_image(parent_work=work)
    save_image(
        image,
        description="an image with every include",
        doc_id="images.everything",
    )


def create_images_linked_with_same_work() -> None:
    parent_work = create_visible_merged_work()
    work_images = [create_extracted_image(parent_work=parent_work) for _ in range(4)]
    other_image = create_extracted_image()

    save_images(
        work_images,
        description="images linked with the same work",
        doc_id="images.examples.linked-with-the-same-work",
    )
    save_image(
        other_image,
        description="images linked with another work",
        doc_id="images.examples.linked-with-another-work",
    )


def create_bread_examples() -> None:
    baguette = create_extracted_image(
        parent_work=create_visible_merged_work(
            title="Baguette is a French style of bread; it's a long, thin bread; other countries also make this bread"
        )
    )
    focaccia = create_extracted_image(
        parent_work=create_visible_merged_work(
            title="A Ligurian style of bread, Focaccia is a flat Italian bread"
        )
    )
    schiacciata = create_extracted_image(
        parent_work=create_visible_merged_work(title="Schiacciata is a Tuscan focaccia")
    )
    mantou = create_extracted_image(
        parent_work=create_visible_merged_work(
            title="Mantou is a steamed bread associated with Northern China"
        )
    )

    save_image(
        baguette,
        description="an example of images with work metadata for the API tests",
        doc_id="images.examples.bread-baguette",
    )
    save_image(
        focaccia,
        description="an example of images with work metadata for the API tests",
        doc_id="images.examples.bread-focaccia",
    )
    save_image(
        schiacciata,
        description="an example of images with work metadata for the API tests",
        doc_id="images.examples.bread-schiacciata",
    )
    save_image(
        mantou,
        description="an example of images with work metadata for the API tests",
        doc_id="images.examples.bread-mantou",
    )


def create_images_with_different_subjects() -> None:
    square_sounds = Subject(
        id=Unidentifiable(), label="Square sounds", concepts=[], type="Subject"
    )
    squashed_squirrels = Subject(
        id=Unidentifiable(), label="Squashed squirrels", concepts=[], type="Subject"
    )
    simple_screwdrivers = Subject(
        id=Identified(
            canonical_id="subject1",
            source_identifier=create_source_identifier(ontology_type="Concept"),
        ),
        label="Simple screwdrivers",
        concepts=[],
        type="Subject",
    )
    struck_samples = Subject(
        id=Identified(
            canonical_id="subject2",
            source_identifier=create_source_identifier(ontology_type="Concept"),
        ),
        label="Struck samples",
        concepts=[],
        type="Subject",
    )

    save_image(
        create_extracted_image(
            parent_work=create_visible_merged_work(subjects=[square_sounds])
        ),
        description="images with different subjects",
        doc_id="images.subjects.sounds",
    )
    save_image(
        create_extracted_image(
            parent_work=create_visible_merged_work(subjects=[simple_screwdrivers])
        ),
        description="images with different subjects",
        doc_id="images.subjects.screwdrivers-1",
    )
    save_image(
        create_extracted_image(
            parent_work=create_visible_merged_work(subjects=[simple_screwdrivers])
        ),
        description="images with different subjects",
        doc_id="images.subjects.screwdrivers-2",
    )
    save_image(
        create_extracted_image(
            parent_work=create_visible_merged_work(
                subjects=[squashed_squirrels, struck_samples]
            )
        ),
        description="images with different subjects",
        doc_id="images.subjects.squirrel,sample",
    )
    save_image(
        create_extracted_image(
            parent_work=create_visible_merged_work(
                subjects=[squashed_squirrels, simple_screwdrivers]
            )
        ),
        description="images with different subjects",
        doc_id="images.subjects.squirrel,screwdriver",
    )


def create_images_with_production_events() -> None:
    for year in ["1900", "1976", "1904", "2020", "1098"]:
        production = [
            ProductionEvent(
                label=random_alphanumeric(25),
                places=[],
                agents=[],
                dates=[create_period_for_year(year)],
            )
        ]
        image = create_extracted_image(
            parent_work=create_visible_merged_work(
                title=f"Production event in {year}",
                production=production,
            )
        )
        save_image(
            image,
            description=f"an image with a production event in {year}",
            doc_id=f"image-production.{year}",
        )


def generate_all() -> None:
    # Reset RNG for deterministic output
    global rng
    rng = random.Random(0)

    create_images_with_different_licenses()
    create_images_with_different_contributors()
    create_images_with_different_genres()
    create_image_without_inferred_data()
    create_contributor_filter_test_examples()
    create_genre_filter_test_examples()
    create_image_with_every_include()
    create_images_linked_with_same_work()
    create_bread_examples()
    create_images_with_different_subjects()
    create_images_with_production_events()

    print(f"Test documents written to {TEST_DOCUMENTS_DIR}")


if __name__ == "__main__":
    generate_all()
