from io import BytesIO
from urllib.parse import unquote_plus
from PIL import Image
from piffle.iiif import IIIFImageClient, ParseError

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
        image_url = str(image.size(width=400, height=400, exact=True))

    image_url = str(image.size(width=400, height=400, exact=False))
    return image_url
