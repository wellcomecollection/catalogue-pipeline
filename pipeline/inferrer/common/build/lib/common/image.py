from io import BytesIO
from urllib.parse import unquote_plus, urlparse

from aiofile import AIOFile
from piffle.image import IIIFImageClient, ParseError
from PIL import Image, UnidentifiedImageError

from common.http import fetch_url_bytes


async def get_image_from_url(image_url, size=None):
    image_url = unquote_plus(image_url)
    parsed_url = urlparse(image_url)
    if parsed_url.scheme == "file":
        image_pointer = await get_local_image(parsed_url.path)
    else:
        image_pointer = await get_remote_image(image_url)

    try:
        image = Image.open(BytesIO(image_pointer))
    except UnidentifiedImageError:
        # If this error gets thrown from inside Pillow, it only has the BytesIO
        # object, and we get the moderately unhelpful error:
        #
        #   cannot identify image file <_io.BytesIO object at 0x7fd5bc7d44d0>
        #
        # Rethrowing it with the image URL should give us more context if/when
        # this error occurs.
        raise UnidentifiedImageError("cannot identify image from URL %r" % image_url)

    if size:
        image = image.resize((size, size), resample=Image.BILINEAR)
    return image


async def get_local_image(path):
    try:
        async with AIOFile(path, "rb") as afp:
            return await afp.read()
    except FileNotFoundError:
        raise ValueError(f"{path} does not exist")


def is_valid_image(response):
    image_formats = ["image/png", "image/jpeg", "image/jpg", "image/jp2"]
    is_valid = (response.status == 200) and (response.content_type in image_formats)
    return is_valid


async def get_remote_image(url):
    try:
        image_url = get_image_url_from_iiif_url(url)
    except ValueError:
        image_url = url

    response = await fetch_url_bytes(image_url)
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
        # DLCS provides a thumbnails service which only serves certain sizes of
        # image. Requests for these don't touch the image server and so, as
        # we're performing lots of requests, we use 400x400 thumbnails and
        # resize them ourselves later on.
        image.api_endpoint = image.api_endpoint.replace("iiif-img", "thumbs")
        # `exact=True` equates to a `/!400,400/` image request
        # https://iiif.io/api/image/1.0/#4-2-size
        return str(image.size(width=400, height=400, exact=True))

    return str(image.size(width=input_size, height=input_size, exact=False))
