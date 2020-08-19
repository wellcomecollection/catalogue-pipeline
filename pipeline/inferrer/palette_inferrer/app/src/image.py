from aiofile import AIOFile
from io import BytesIO
from urllib.parse import unquote_plus, urlparse
from PIL import Image
from piffle.iiif import IIIFImageClient, ParseError

from .http import fetch_url_bytes


async def get_image_from_url(image_url):
    image_url = unquote_plus(image_url)
    parsed_url = urlparse(image_url)
    if parsed_url.scheme == "file":
        image_pointer = await get_local_image(parsed_url.path)
    else:
        image_pointer = await get_remote_image(image_url)
    return Image.open(BytesIO(image_pointer))


async def get_local_image(path):
    async with AIOFile(path, "rb") as afp:
        return await afp.read()


def is_valid_image(response):
    image_formats = ["image/png", "image/jpeg", "image/jpg", "image/jp2"]
    return (response.status == 200) and (response.content_type in image_formats)


async def get_remote_image(url):
    response = await fetch_url_bytes(url)
    if is_valid_image(response["object"]):
        return response["bytes"]
    else:
        raise ValueError(f"{url} is not a valid image URL")


def get_image_url_from_iiif_url(iiif_url):
    try:
        image = IIIFImageClient.init_from_url(iiif_url)
    except ParseError:
        raise ValueError(f"{iiif_url} is not a valid iiif URL")

    return str(image.size(width=400, height=400, exact=False))
