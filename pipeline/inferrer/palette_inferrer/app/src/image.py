from aiofile import AIOFile
from io import BytesIO
from urllib.parse import unquote_plus, urlparse
from PIL import Image
from piffle.iiif import IIIFImageClient, ParseError

from .http import fetch_url_bytes


async def get_image_from_url(image_url, size=None):
    image_url = unquote_plus(image_url)
    parsed_url = urlparse(image_url)
    if parsed_url.scheme == "file":
        image_pointer = await get_local_image(parsed_url.path)
    else:
        image_pointer = await get_remote_image(image_url)
    image = Image.open(BytesIO(image_pointer))
    if size:
        image = image.resize((size, size), resample=Image.BILINEAR)
    return image


async def get_local_image(path):
    try:
        async with AIOFile(path, "rb") as afp:
            return await afp.read()
    except FileNotFoundError:
        return ValueError(f"{path} does not exist")


def is_valid_image(response):
    image_formats = ["image/png", "image/jpeg", "image/jpg", "image/jp2"]
    return (response.status == 200) and (response.content_type in image_formats)


async def get_remote_image(url):
    response = await fetch_url_bytes(url)
    if is_valid_image(response["object"]):
        return response["bytes"]
    else:
        raise ValueError(f"{url} is not a valid image URL")


def get_image_url_from_iiif_url(iiif_url, input_size=224):
    try:
        image = IIIFImageClient.init_from_url(iiif_url)
    except ParseError:
        raise ValueError(f"{iiif_url} is not a valid iiif URL")

    if "dlcs" in image.api_endpoint:
        # DLCS provides a thumbnails service which only serves certain sizes of image.
        # Requests for these don't touch the image server and so, as we're performing
        # lots of requests, we use 400x400 thumbnails and resize them ourselves later on.
        image.api_endpoint = image.api_endpoint.replace("iiif-img", "thumbs")
        # `exact=True` equates to a `/!400,400/` image request
        # https://iiif.io/api/image/1.0/#4-2-size
        return str(image.size(width=400, height=400, exact=True))

    return str(
        image.size(width=input_size, height=input_size, exact=False)
    )
