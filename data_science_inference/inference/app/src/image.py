from io import BytesIO
from urllib.parse import unquote_plus

import requests
from PIL import Image


def is_valid_image_url(image_url):
    image_formats = ['image/png', 'image/jpeg', 'image/jpg']
    try:
        r = requests.head(image_url)
        if (r.status_code == 200) and (r.headers['content-type'] in image_formats):
            return True
        return False
    except:
        return False


def is_valid_iiif_url(iiif_url):
    try:
        r = requests.head(iiif_url)
        if (r.status_code == 200) and (r.headers['content-type'] == 'application/json'):
            return True
        return False
    except:
        return False


def get_image_from_url(image_url):
    image_url = unquote_plus(image_url)
    if is_valid_image_url(image_url):
        r = requests.get(image_url)
        image = Image.open(BytesIO(r.content))
        return image
    else:
        raise ValueError(f'{image_url} is not a valid image URL')


def get_image_url_from_iiif_url(iiif_url):
    if is_valid_iiif_url(iiif_url):
        url = unquote_plus(iiif_url)
        image_url = url.replace('info.json', '/full/760,/0/default.jpg')
        return image_url
    else:
        raise ValueError(f'{iiif_url} is not a valid iiif URL')
