from io import BytesIO
from urllib.parse import unquote_plus
from PIL import Image

from .http import fetch_url_bytes


def is_valid_image(response):
    image_formats = ["image/png", "image/jpeg", "image/jpg", "image/jp2"]
    return (response.status == 200) and (response.content_type in image_formats)


async def get_image_from_url(image_url):
    image_url = unquote_plus(image_url)
    response = await fetch_url_bytes(image_url)
    if is_valid_image(response["object"]):
        image_bytes = BytesIO(response["bytes"])
        image = Image.open(image_bytes)
        return image
    else:
        raise ValueError(f"{image_url} is not a valid image URL")


def get_image_url_from_iiif_url(iiif_url):
    if iiif_url.endswith("info.json"):
        url = unquote_plus(iiif_url)
        image_url = url.replace("info.json", "/full/760,/0/default.jpg")
        return image_url
    else:
        raise ValueError(f"{iiif_url} is not a valid iiif URL")
