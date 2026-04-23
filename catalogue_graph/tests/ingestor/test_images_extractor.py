import copy

from ingestor.extractors.images.images_extractor import (
    ExtractedImage,
    GraphImagesExtractor,
)
from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.augmented.image import AugmentedImage
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
)
from models.events import BasePipelineEvent
from tests.mocks import (
    MockElasticsearchClient,
    add_neptune_mock_response,
    get_mock_es_client,
    get_mock_neptune_client,
)
from tests.test_utils import load_json_fixture

MOCK_EVENT = BasePipelineEvent(pipeline_date="dev")

IMAGE_FIXTURE = load_json_fixture("ingestor/single_augmented_image.json")
MERGED_FIXTURE = load_json_fixture("ingestor/single_merged.json")


def get_extractor() -> GraphImagesExtractor:
    return GraphImagesExtractor(
        MOCK_EVENT,
        get_mock_es_client("graph_extractor", MOCK_EVENT.pipeline_date),
        get_mock_neptune_client(),
    )


def _get_image_fixture(image_id: str, work_id: str) -> dict:
    fixture: dict = copy.deepcopy(IMAGE_FIXTURE)
    fixture["state"]["canonicalId"] = image_id
    fixture["source"]["id"]["canonicalId"] = work_id
    return fixture


def mock_es_image(image_id: str, work_id: str) -> None:
    fixture = _get_image_fixture(image_id, work_id)
    MockElasticsearchClient.index("images-augmented-dev", image_id, fixture)


def mock_es_work(work_id: str) -> None:
    fixture = copy.deepcopy(MERGED_FIXTURE)
    fixture["state"]["canonicalId"] = work_id
    MockElasticsearchClient.index("works-denormalised-dev", work_id, fixture)


def mock_graph_relationships(work_ids: list[str]) -> None:
    add_neptune_mock_response(WORK_ANCESTORS_QUERY, {"ids": work_ids}, [])
    add_neptune_mock_response(WORK_CHILDREN_QUERY, {"ids": work_ids}, [])


def test_extract_image_with_visible_work() -> None:
    mock_es_image("img1", "work1")
    mock_es_work("work1")
    mock_graph_relationships(["work1"])

    extracted = list(get_extractor().extract_raw())

    assert len(extracted) == 1
    assert isinstance(extracted[0], ExtractedImage)
    assert extracted[0].image.state.canonical_id == "img1"
    assert extracted[0].work.work.state.canonical_id == "work1"


def test_image_without_visible_work_is_skipped() -> None:
    """When an image's parent work is not visible, the image is not yielded."""
    mock_es_image("img2", "missing_work")
    # Don't add the work to ES — it won't be found
    mock_graph_relationships(["missing_work"])

    extracted = list(get_extractor().extract_raw())

    assert len(extracted) == 0


def test_multiple_images_same_work() -> None:
    mock_es_image("imgA", "work1")
    mock_es_image("imgB", "work1")
    mock_es_work("work1")
    mock_graph_relationships(["work1"])

    extracted = list(get_extractor().extract_raw())

    assert len(extracted) == 2
    image_ids = {e.image.state.canonical_id for e in extracted}
    assert image_ids == {"imgA", "imgB"}
    # Both images should reference the same work
    for e in extracted:
        assert e.work.work.state.canonical_id == "work1"


def test_multiple_images_different_works() -> None:
    mock_es_image("imgX", "work1")
    mock_es_image("imgY", "work2")
    mock_es_work("work1")
    mock_es_work("work2")
    mock_graph_relationships(["work1", "work2"])

    extracted = list(get_extractor().extract_raw())

    assert len(extracted) == 2
    image_ids = {e.image.state.canonical_id for e in extracted}
    assert image_ids == {"imgX", "imgY"}


def test_extracted_image_contains_augmented_image_model() -> None:
    mock_es_image("img1", "work1")
    mock_es_work("work1")
    mock_graph_relationships(["work1"])

    extracted = list(get_extractor().extract_raw())

    assert len(extracted) == 1
    image = extracted[0].image
    assert isinstance(image, AugmentedImage)
    assert image.state.inferred_data.features == [0.1, 0.2, 0.3]
    assert image.state.inferred_data.palette_embedding == [0.4, 0.5, 0.6]
    assert image.state.inferred_data.average_color_hex == "#a1b2c3"
    assert image.state.inferred_data.aspect_ratio == 1.5


def test_extracted_work_has_hierarchy() -> None:
    mock_es_image("img1", "work1")
    mock_es_work("work1")
    mock_graph_relationships(["work1"])

    extracted = list(get_extractor().extract_raw())

    assert len(extracted) == 1
    work = extracted[0].work
    assert isinstance(work, VisibleExtractedWork)
    assert work.hierarchy == WorkHierarchy(id="work1", ancestors=[], children=[])
