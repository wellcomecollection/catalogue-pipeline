from pathlib import Path

import pytest
import requests
from _pytest.monkeypatch import MonkeyPatch

from inferrer import image_downloader
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
from tests.mocks import MockRequest, MockResponse


def _patch_get_sequence(monkeypatch: MonkeyPatch, items: list) -> dict:
    """Patch requests.get to return/raise each item in turn; counts calls."""
    seq = iter(items)
    state = {"calls": 0}

    def fake_get(url: str, timeout: float | None = None, **kwargs: object) -> object:
        state["calls"] += 1
        item = next(seq)
        if isinstance(item, Exception):
            raise item
        return item

    monkeypatch.setattr(image_downloader.requests, "get", fake_get)
    monkeypatch.setattr(image_downloader, "DOWNLOAD_BACKOFF_SECONDS", 0.0)
    return state


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


def test_download_retries_transient_status_then_succeeds(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    image = make_initial_image("imgA", "http://iiif.test/image/imgA/info.json")
    state = _patch_get_sequence(
        monkeypatch, [MockResponse(502), MockResponse(200, content=b"jpeg-bytes")]
    )

    path = download_image(image, str(tmp_path), timeout=5)

    assert path.read_bytes() == b"jpeg-bytes"
    assert state["calls"] == 2


def test_download_retries_connection_error_then_succeeds(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    image = make_initial_image("imgA", "http://iiif.test/image/imgA/info.json")
    state = _patch_get_sequence(
        monkeypatch,
        [requests.exceptions.ConnectionError("boom"), MockResponse(200, content=b"ok")],
    )

    path = download_image(image, str(tmp_path), timeout=5)

    assert path.read_bytes() == b"ok"
    assert state["calls"] == 2


def test_download_raises_after_exhausting_transient_retries(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    monkeypatch.setattr(image_downloader, "MAX_DOWNLOAD_ATTEMPTS", 3)
    image = make_initial_image("imgA", "http://iiif.test/image/imgA/info.json")
    state = _patch_get_sequence(monkeypatch, [MockResponse(503)] * 3)

    with pytest.raises(ImageDownloadError, match="after 3 attempts"):
        download_image(image, str(tmp_path), timeout=5)
    assert state["calls"] == 3


def test_download_does_not_retry_permanent_status(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    image = make_initial_image("imgA", "http://iiif.test/image/imgA/info.json")
    state = _patch_get_sequence(monkeypatch, [MockResponse(404)])

    with pytest.raises(ImageDownloadError, match="status 404"):
        download_image(image, str(tmp_path), timeout=5)
    assert state["calls"] == 1
