from pathlib import Path

import pytest

from inferrer.image_downloader import (
    ImageDownloadError,
    delete_image,
    download_image,
    file_url,
    get_image_url,
    local_image_path,
)
from inferrer.models import InitialImage
from tests.inferrer.factories import initial_image_doc, make_initial_image
from tests.mocks import MockRequest


def test_get_image_url_rewrites_info_json_to_thumbnail() -> None:
    image = make_initial_image("img1", "http://iiif.test/image/V1/info.json")
    assert get_image_url(image) == (
        "http://iiif.test/image/V1/full/!400,400/0/default.jpg"
    )


def test_get_image_url_passes_through_non_info_json() -> None:
    image = make_initial_image("img1", "http://iiif.test/image/V1.jpg")
    assert get_image_url(image) == "http://iiif.test/image/V1.jpg"


def test_get_image_url_none_when_no_iiif_location() -> None:
    doc = initial_image_doc("img1", "http://x")
    doc["locations"][0]["locationType"] = {"id": "online-resource"}
    image = InitialImage.model_validate(doc)
    assert get_image_url(image) is None


def test_download_then_delete_cleans_up_directory(tmp_path: Path) -> None:
    image = make_initial_image("imgA", "http://iiif.test/image/imgA/info.json")
    thumbnail_url = "http://iiif.test/image/imgA/full/!400,400/0/default.jpg"
    MockRequest.mock_response(
        method="GET", url=thumbnail_url, content_bytes=b"jpeg-bytes"
    )

    path = download_image(image, str(tmp_path), timeout=5)

    assert path == local_image_path(image, str(tmp_path))
    assert path.read_bytes() == b"jpeg-bytes"
    assert file_url(path).startswith("file://")

    delete_image(path, str(tmp_path))
    assert not path.exists()
    # The per-image directory is removed too, but the root is left intact.
    assert not path.parent.exists()
    assert tmp_path.exists()


def test_download_raises_without_iiif_location(tmp_path: Path) -> None:
    doc = initial_image_doc("imgA", "http://x")
    doc["locations"][0]["locationType"] = {"id": "online-resource"}
    image = InitialImage.model_validate(doc)
    with pytest.raises(ImageDownloadError):
        download_image(image, str(tmp_path), timeout=5)
