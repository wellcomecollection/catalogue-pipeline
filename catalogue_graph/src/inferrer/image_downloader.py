"""Download IIIF thumbnails for inference and clean them up afterwards.

Ports `inference_manager/.../services/ImageDownloader.scala`. We fetch the DLCS
400x400 thumbnail (cheap, served from the thumbnail service rather than the
image server), write it to a per-image directory under a shared root, hand the
inferrers a `file://` URL, and delete the file (and its now-empty directory)
once inference is done.
"""

from __future__ import annotations

from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

import requests

from inferrer.models import InitialImage

IIIF_IMAGE_LOCATION_TYPE = "iiif-image"
INFO_JSON = "info.json"
# DLCS serves a fixed set of thumbnail sizes without touching the image server.
THUMBNAIL_SUFFIX = "full/!400,400/0/default.jpg"


class ImageDownloadError(Exception):
    pass


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


def download_image(image: InitialImage, root: str, timeout: float) -> Path:
    url = get_image_url(image)
    if url is None:
        raise ImageDownloadError(
            f"Could not extract an image URL from locations on image "
            f"{image.state.source_identifier}"
        )

    response = requests.get(url, timeout=timeout)
    if response.status_code != 200:
        raise ImageDownloadError(
            f"Image request for {url} failed with status {response.status_code}"
        )

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
