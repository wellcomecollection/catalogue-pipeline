import copy

from freezegun import freeze_time
from ingestor.extractors.images.images_extractor import ExtractedImage
from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.aggregate.image import ImageAggregatableValues
from ingestor.models.augmented.image import AugmentedImage
from ingestor.models.display.image import DisplayImage, DisplayImageSource
from ingestor.models.filter.image import ImageFilterableValues
from ingestor.models.indexable.image import IndexableImage
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.models.query.image import QueryImage
from ingestor.models.query.work import QueryWork
from ingestor.models.vector.image import ImageVectorValues
from ingestor.transformers.images_transformer import IngestorImagesTransformer
from models.events import BasePipelineEvent

from tests.mocks import get_mock_es_client, get_mock_neptune_client
from tests.test_utils import load_json_fixture

MOCK_EVENT = BasePipelineEvent(pipeline_date="dev")

IMAGE_FIXTURE = load_json_fixture("ingestor/single_augmented_image.json")
MERGED_FIXTURE = load_json_fixture("ingestor/single_merged.json")

MOCK_WORK_ID = "a24esypq"
MOCK_IMAGE_ID = "swms7qdx"

# Enrich the merged work with a contributor, subject, and genre
MOCK_CONTRIBUTOR = {
    "id": {"type": "Unidentifiable"},
    "agent": {
        "id": {
            "canonicalId": "uykuavkt",
            "sourceIdentifier": {
                "identifierType": {"id": "label-derived"},
                "ontologyType": "Person",
                "value": "gamelin, jacques, 1739-1803",
            },
            "otherIdentifiers": [],
            "type": "Identified",
        },
        "label": "Gamelin, Jacques, 1739-1803.",
        "type": "Person",
    },
    "roles": [],
    "primary": True,
}

MOCK_SUBJECT = {
    "id": {
        "canonicalId": "s6s24vd7",
        "sourceIdentifier": {
            "identifierType": {"id": "lc-subjects"},
            "ontologyType": "Concept",
            "value": "sh85004839",
        },
        "otherIdentifiers": [],
        "type": "Identified",
    },
    "label": "Human anatomy",
    "concepts": [
        {
            "id": {
                "canonicalId": "s6s24vd7",
                "sourceIdentifier": {
                    "identifierType": {"id": "lc-subjects"},
                    "ontologyType": "Concept",
                    "value": "sh85004839",
                },
                "otherIdentifiers": [],
                "type": "Identified",
            },
            "label": "Human anatomy",
            "type": "Concept",
        }
    ],
}

MOCK_GENRE = {
    "label": "Etching",
    "concepts": [
        {
            "id": {
                "canonicalId": "yfqryj28",
                "sourceIdentifier": {
                    "identifierType": {"id": "label-derived"},
                    "ontologyType": "Genre",
                    "value": "etching",
                },
                "otherIdentifiers": [],
                "type": "Identified",
            },
            "label": "Etching",
            "type": "GenreConcept",
        }
    ],
}


def _build_merged_fixture() -> dict:
    fixture: dict = copy.deepcopy(MERGED_FIXTURE)
    fixture["data"]["contributors"] = [MOCK_CONTRIBUTOR]
    fixture["data"]["subjects"] = [MOCK_SUBJECT]
    fixture["data"]["genres"] = [MOCK_GENRE]
    return fixture


def _build_extracted_image() -> ExtractedImage:
    image = AugmentedImage.model_validate(IMAGE_FIXTURE)
    work = VisibleMergedWork(**_build_merged_fixture())
    hierarchy = WorkHierarchy(id=MOCK_WORK_ID, ancestors=[], children=[])
    extracted_work = VisibleExtractedWork(work=work, hierarchy=hierarchy, concepts=[])
    return ExtractedImage(image=image, work=extracted_work)


def get_transformer() -> IngestorImagesTransformer:
    return IngestorImagesTransformer(
        MOCK_EVENT,
        get_mock_es_client("graph_extractor", MOCK_EVENT.pipeline_date),
        get_mock_neptune_client(),
    )


@freeze_time("2025-04-21T12:00:00Z")
def test_transform_document() -> None:
    extracted = _build_extracted_image()
    transformer = get_transformer()

    result = transformer.transform_document(extracted)

    assert isinstance(result, IndexableImage)
    assert result.get_id() == MOCK_IMAGE_ID
    assert result.modified_time == "2025-04-01T10:00:00Z"


@freeze_time("2025-04-21T12:00:00Z")
def test_display_image() -> None:
    extracted = _build_extracted_image()

    result = DisplayImage.from_extracted_image(extracted)

    assert result.id == MOCK_IMAGE_ID
    assert result.type == "Image"
    assert result.aspect_ratio == 1.5
    assert result.average_color == "#a1b2c3"
    assert len(result.locations) == 1
    assert (
        result.locations[0].url
        == "https://iiif.wellcomecollection.org/image/V0017087/info.json"
    )
    assert result.locations[0].locationType.id == "iiif-image"


@freeze_time("2025-04-21T12:00:00Z")
def test_display_image_thumbnail() -> None:
    extracted = _build_extracted_image()

    result = DisplayImage.from_extracted_image(extracted)

    thumbnail = result.thumbnail
    assert thumbnail.locationType.id == "iiif-image"
    assert (
        thumbnail.url == "https://iiif.wellcomecollection.org/image/V0017087/info.json"
    )


@freeze_time("2025-04-21T12:00:00Z")
def test_display_image_source() -> None:
    extracted = _build_extracted_image()

    result = DisplayImageSource.from_extracted_image(extracted)

    assert result.id == MOCK_WORK_ID
    assert result.title == "A test work title"
    assert result.type == "Work"
    assert len(result.contributors) == 1
    assert result.contributors[0].agent.label == "Gamelin, Jacques, 1739-1803."
    assert len(result.subjects) == 1
    assert result.subjects[0].label == "Human anatomy"
    assert len(result.genres) == 1
    assert result.genres[0].label == "Etching"


def test_query_image() -> None:
    extracted = _build_extracted_image()

    result = QueryImage.from_extracted_image(extracted)

    assert result.id == MOCK_IMAGE_ID
    assert isinstance(result.source, QueryWork)


def test_vector_values() -> None:
    image = AugmentedImage.model_validate(IMAGE_FIXTURE)

    result = ImageVectorValues.from_augmented_image(image)

    assert result.features == [0.1, 0.2, 0.3]
    assert result.palette_embedding == [0.4, 0.5, 0.6]


def test_filterable_values() -> None:
    extracted = _build_extracted_image()

    result = ImageFilterableValues.from_extracted_image(extracted)

    assert result.locations_license_id == ["cc-by"]
    assert result.source_contributors_agent_label == ["Gamelin, Jacques, 1739-1803."]
    assert result.source_contributors_agent_id == ["uykuavkt"]
    assert result.source_genres_label == ["Etching"]
    assert result.source_subjects_label == ["Human anatomy"]


def test_aggregatable_values() -> None:
    extracted = _build_extracted_image()

    result = ImageAggregatableValues.from_extracted_image(extracted)

    assert len(result.contributors) == 1
    assert result.contributors[0].id == "uykuavkt"
    assert result.contributors[0].label == "Gamelin, Jacques, 1739-1803."
    assert len(result.genres) == 1
    assert result.genres[0].id == "yfqryj28"
    assert result.genres[0].label == "Etching"
    assert len(result.subjects) == 1
    assert result.subjects[0].id == "s6s24vd7"
    assert result.subjects[0].label == "Human anatomy"


@freeze_time("2025-04-21T12:00:00Z")
def test_debug_indexed_time() -> None:
    extracted = _build_extracted_image()

    result = IndexableImage.from_extracted_image(extracted)

    assert result.debug.indexed_time.startswith("2025-04-21")


@freeze_time("2025-04-21T12:00:00Z")
def test_display_image_defaults_for_missing_inferred_data() -> None:
    """When inferred_data fields are None, defaults are used."""
    fixture = copy.deepcopy(IMAGE_FIXTURE)
    fixture["state"]["inferredData"]["averageColorHex"] = None
    fixture["state"]["inferredData"]["aspectRatio"] = None

    image = AugmentedImage.model_validate(fixture)
    work = VisibleMergedWork(**MERGED_FIXTURE)
    hierarchy = WorkHierarchy(id=MOCK_WORK_ID, ancestors=[], children=[])
    extracted_work = VisibleExtractedWork(work=work, hierarchy=hierarchy, concepts=[])
    extracted = ExtractedImage(image=image, work=extracted_work)

    result = DisplayImage.from_extracted_image(extracted)

    assert result.aspect_ratio == 1
    assert result.average_color == "#ffffff"
