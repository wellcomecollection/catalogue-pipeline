from datetime import datetime
from pathlib import Path

import pytest

from inferrer.adapters import FEATURE_VECTOR_SIZE, INFERRERS
from inferrer.image_downloader import file_url, local_image_path
from inferrer.models import InferenceManagerEvent
from inferrer.steps import inference_manager
from inferrer.steps.inference_manager import (
    PoisonedImageError,
    generate_operations,
    validate_inferred,
)
from ingestor.models.augmented.image import InferredData
from tests.inferrer.factories import (
    encode_floats,
    initial_image_doc,
    make_initial_image,
)
from tests.mocks import MockElasticsearchClient, MockRequest, mock_es_secrets

PIPELINE_DATE = "2026-06-01"
THUMBNAIL_URL = "http://iiif.test/image/imgA/full/!400,400/0/default.jpg"
INFO_JSON_URL = "http://iiif.test/image/imgA/info.json"


def _valid_inferred(**overrides: object) -> InferredData:
    base: dict = {
        "features": [0.1] * FEATURE_VECTOR_SIZE,
        "palette_embedding": [0.2, 0.3],
        "average_color_hex": "#abcdef",
        "aspect_ratio": 1.2,
    }
    base.update(overrides)
    return InferredData(**base)


def _mock_inferrers(query_url: str, features: list[float] | None = None) -> None:
    features = features if features is not None else [0.1] * FEATURE_VECTOR_SIZE
    responses: dict[str, dict] = {
        "feature": {"features_b64": encode_floats(features)},
        "palette": {
            "palette_embedding": encode_floats([0.2, 0.3]),
            "average_color_hex": "#abcdef",
        },
        "aspect_ratio": {"aspect_ratio": 1.2},
    }
    for inferrer in INFERRERS:
        MockRequest.mock_response(
            method="GET",
            url=f"{inferrer.base_url}{inferrer.path}",
            params={"query_url": query_url},
            json_data=responses[inferrer.name],
        )


def test_validate_inferred_accepts_complete_data() -> None:
    validate_inferred("img1", _valid_inferred())


@pytest.mark.parametrize(
    "overrides",
    [
        {"features": [0.1] * 10},
        {"features": []},
        {"palette_embedding": []},
        {"average_color_hex": None},
        {"aspect_ratio": None},
    ],
)
def test_validate_inferred_rejects_poisoned_data(overrides: dict) -> None:
    with pytest.raises(PoisonedImageError):
        validate_inferred("img1", _valid_inferred(**overrides))


def test_generate_operations_uses_external_gte_versioning() -> None:
    image = make_initial_image("imgA", INFO_JSON_URL)
    augmented = inference_manager._build_augmented(image, _valid_inferred())

    operations = list(
        generate_operations(f"images-augmented-{PIPELINE_DATE}", [augmented])
    )

    expected_version = int(
        datetime.fromisoformat("2026-06-10T12:00:00Z").timestamp() * 1000
    )
    assert operations == [
        {
            "_index": f"images-augmented-{PIPELINE_DATE}",
            "_id": "imgA",
            "_source": operations[0]["_source"],
            "_version": expected_version,
            "_version_type": "external_gte",
        }
    ]
    # augmentedTime and inferredData are present in the serialised state.
    assert "augmentedTime" in operations[0]["_source"]["state"]
    assert len(operations[0]["_source"]["state"]["inferredData"]["features"]) == (
        FEATURE_VECTOR_SIZE
    )


def test_handler_indexes_augmented_image(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(inference_manager, "IMAGES_ROOT", str(tmp_path))
    mock_es_secrets("inferrer", PIPELINE_DATE)

    image = make_initial_image("imgA", INFO_JSON_URL)
    MockElasticsearchClient.index(
        index=f"images-initial-{PIPELINE_DATE}",
        id="imgA",
        document=initial_image_doc("imgA", INFO_JSON_URL),
    )
    MockRequest.mock_response(method="GET", url=THUMBNAIL_URL, content_bytes=b"jpeg")
    _mock_inferrers(file_url(local_image_path(image, str(tmp_path))))

    event = InferenceManagerEvent(pipeline_date=PIPELINE_DATE, ids=["imgA"])
    result = inference_manager.handler(event, es_mode="private")

    assert result.processed == 1
    assert result.augmented == 1

    indexed = MockElasticsearchClient.inputs
    assert len(indexed) == 1
    assert indexed[0]["_index"] == f"images-augmented-{PIPELINE_DATE}"
    assert indexed[0]["_id"] == "imgA"
    assert "augmentedTime" in indexed[0]["_source"]["state"]


def test_handler_fails_and_indexes_nothing_on_poison(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(inference_manager, "IMAGES_ROOT", str(tmp_path))
    mock_es_secrets("inferrer", PIPELINE_DATE)

    image = make_initial_image("imgA", INFO_JSON_URL)
    MockElasticsearchClient.index(
        index=f"images-initial-{PIPELINE_DATE}",
        id="imgA",
        document=initial_image_doc("imgA", INFO_JSON_URL),
    )
    MockRequest.mock_response(method="GET", url=THUMBNAIL_URL, content_bytes=b"jpeg")
    # Feature vector of the wrong length -> poisoned doc -> the task must fail.
    _mock_inferrers(
        file_url(local_image_path(image, str(tmp_path))), features=[0.1] * 10
    )

    event = InferenceManagerEvent(pipeline_date=PIPELINE_DATE, ids=["imgA"])
    with pytest.raises(PoisonedImageError):
        inference_manager.handler(event, es_mode="private")

    assert MockElasticsearchClient.inputs == []
