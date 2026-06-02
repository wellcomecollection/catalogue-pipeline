"""
Creates example image documents for catalogue API tests using deterministic random data via a seeded RNG.

Run from the catalogue_graph directory:
    uv run python -m document_generators.create_test_image_documents
"""

from typing import Any

from freezegun import freeze_time

from ingestor.extractors.images.images_extractor import ExtractedImage
from ingestor.models.augmented.image import InferredData
from ingestor.models.indexable.image import IndexableImage
from models.pipeline.concept import Subject
from models.pipeline.id_label import Language
from models.pipeline.identifier import Identified
from models.pipeline.production import ProductionEvent

from .generators import (
    create_concept,
    create_contributor,
    create_extracted_image,
    create_genre,
    create_genre_concept,
    create_period_for_year,
    create_source_identifier,
    create_subject,
    create_visible_merged_work,
    random_alphanumeric,
    reset,
)
from .utils import TEST_DOCUMENTS_DIR, save_document


@freeze_time("2001-01-01T01:01:01Z")
def transform_image(extracted: ExtractedImage) -> dict[str, Any]:
    indexable = IndexableImage.from_extracted_image(extracted)
    return indexable.model_dump(mode="json", exclude_none=True)


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
    carrots = create_contributor("carrots", concept_type="Agent")
    parrots = create_contributor("parrots", concept_type="Organisation")
    parrots_meeting = create_contributor("parrots", concept_type="Meeting")

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
    machiavelli = create_contributor("Machiavelli, Niccolo", concept_type="Person")
    hypatia = create_contributor("Hypatia", concept_type="Person")
    said = create_contributor("Edward Said", concept_type="Person")

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
            create_contributor("Adrian Aardvark", concept_type="Person"),
            create_contributor("Beatrice Buffalo", concept_type="Person"),
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

    description = "an example of images with work metadata for the API tests"
    save_image(
        baguette,
        description=description,
        doc_id="images.examples.bread-baguette",
    )
    save_image(
        focaccia,
        description=description,
        doc_id="images.examples.bread-focaccia",
    )
    save_image(
        schiacciata,
        description=description,
        doc_id="images.examples.bread-schiacciata",
    )
    save_image(
        mantou,
        description=description,
        doc_id="images.examples.bread-mantou",
    )


def create_images_with_different_subjects() -> None:
    square_sounds = create_subject("Square sounds", concepts=[])
    squashed_squirrels = create_subject("Squashed squirrels", concepts=[])
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
    reset()

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
