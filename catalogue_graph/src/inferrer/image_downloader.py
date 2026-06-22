"""Download IIIF thumbnails for inference and clean them up afterwards.

Ports `inference_manager/.../services/ImageDownloader.scala`. We fetch the DLCS
400x400 thumbnail (cheap, served from the thumbnail service rather than the
image server), write it to a per-image directory under a shared root, hand the
inferrers a `file://` URL, and delete the file (and its now-empty directory)
once inference is done.
"""

from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

import backoff
import requests

from inferrer.models import InitialImage

IIIF_IMAGE_LOCATION_TYPE = "iiif-image"
INFO_JSON = "info.json"
# DLCS serves a fixed set of thumbnail sizes without touching the image server.
THUMBNAIL_SUFFIX = "full/!400,400/0/default.jpg"

# Transient HTTP statuses worth retrying: gateway/overload errors from the IIIF
# thumbnail service that typically clear on a retry. A single un-retried 502 here
# fails the whole all-or-nothing inference task (and, with the state machine's
# fail-fast Map, can abort an entire run), so retry these rather than failing.
TRANSIENT_STATUS_CODES = frozenset({429, 500, 502, 503, 504})
MAX_DOWNLOAD_ATTEMPTS = int(os.environ.get("IMAGE_DOWNLOAD_MAX_ATTEMPTS", "4"))
DOWNLOAD_BACKOFF_SECONDS = float(
    os.environ.get("IMAGE_DOWNLOAD_BACKOFF_SECONDS", "0.5")
)


class ImageDownloadError(Exception):
    pass


class _TransientImageDownloadError(ImageDownloadError):
    """A retryable download failure (a transient HTTP status).

    Subclasses `ImageDownloadError` so that, once the `backoff` retries are
    exhausted and this propagates, callers catching `ImageDownloadError` still
    handle it.
    """


def _to_thumbnail_url(url: str) -> str:
    parts = urlsplit(url)
    if parts.path.endswith(INFO_JSON):
        new_path = parts.path[: -len(INFO_JSON)] + THUMBNAIL_SUFFIX
        return urlunsplit(parts._replace(path=new_path))
    return url


def get_image_url(image: InitialImage) -> str | None:
    for location in image.locations:
        if location.location_type.id == IIIF_IMAGE_LOCATION_TYPE:
            return _to_thumbnail_url(location.url)
    return None


def local_image_path(image: InitialImage, root: str) -> Path:
    return Path(root, image.state.id(), "default.jpg").resolve()


def file_url(path: Path) -> str:
    # Matches the Scala `Uri.from(scheme = "file", path = ...)` -> file:///...
    return path.as_uri()


@backoff.on_exception(
    backoff.expo,
    (_TransientImageDownloadError, requests.exceptions.RequestException),
    max_tries=lambda: MAX_DOWNLOAD_ATTEMPTS,
    factor=DOWNLOAD_BACKOFF_SECONDS,
)
def _fetch_image(url: str, timeout: float) -> requests.Response:
    """GET the thumbnail, retrying transient failures with exponential backoff.

    The `backoff` decorator retries transient HTTP statuses (raised as
    `_TransientImageDownloadError`) and transport errors
    (`requests.exceptions.RequestException`); a non-transient bad status (e.g.
    404) raises `ImageDownloadError` immediately since retrying will not help.
    """
    response = requests.get(url, timeout=timeout)
    if response.status_code == 200:
        return response

    message = f"Image request for {url} failed with status {response.status_code}"
    if response.status_code in TRANSIENT_STATUS_CODES:
        raise _TransientImageDownloadError(message)
    raise ImageDownloadError(message)


def download_image(image: InitialImage, root: str, timeout: float) -> Path:
    url = get_image_url(image)
    if url is None:
        raise ImageDownloadError(
            f"Could not extract an image URL from locations on image "
            f"{image.state.source_identifier}"
        )

    response = _fetch_image(url, timeout)

    path = local_image_path(image, root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(response.content)
    return path


def delete_image(path: Path, root: str) -> None:
    """Delete the image file and any now-empty parent directories up to root."""
    root_path = Path(root).resolve()
    path.unlink(missing_ok=True)

    parent = path.parent
    while parent != root_path and parent != parent.parent:
        try:
            parent.rmdir()  # only succeeds if the directory is empty
        except OSError:
            break
        parent = parent.parent
