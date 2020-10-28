#!/usr/bin/env python
"""
This script will download all the images from a work, and saves them to
a local directory.

Example usage:

    $ python3 download_images_on_work.py "https://wellcomecollection.org/works/fecwftff"

"""

import os
import sys

import httpx
import hyperlink

from _downloads import download_digital_location
from _git_helpers import ignore_directory


def get_work_id(url):
    """
    Given a URL of the form https://wellcomecollection.org/works/acv8r4bv,
    extract the work ID (in this case ``acv8r4bv``).
    """
    parsed_url = hyperlink.URL.from_text(url)

    if parsed_url.host != "wellcomecollection.org":
        raise RuntimeError(f"Not a wellcomecollection.org URL: {url}")

    try:
        works, work_id = parsed_url.path
        assert works == "works"
    except (AssertionError, ValueError):  # wrong number of path parts, or not /works
        raise RuntimeError(f"Is this a /works URL? {url}")

    return work_id


def get_digital_locations(work_id):
    resp = httpx.get(
        f"https://api.wellcomecollection.org/catalogue/v2/works/{work_id}",
        params={"include": "images,items"},
    )
    work = resp.json()

    for item in work.get("items", []):
        for loc in item["locations"]:
            if loc["type"] == "DigitalLocation":
                yield loc

    for w_image in work.get("images", []):
        resp = httpx.get(
            f"https://api.wellcomecollection.org/catalogue/v2/images/{w_image['id']}"
        )

        image = resp.json()

        for loc in image["locations"]:
            if loc["type"] == "DigitalLocation":
                yield loc


if __name__ == "__main__":
    try:
        works_url = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <WORKS_URL>")

    try:
        work_id = get_work_id(works_url)
    except RuntimeError as err:
        sys.exit(str(err))

    os.makedirs(work_id, exist_ok=True)
    ignore_directory(work_id)

    with open(work_id + "/README.txt", "w") as outfile:
        outfile.write(f"Images from {works_url}\n")

    for i, location in enumerate(get_digital_locations(work_id), start=1):
        download_digital_location(
            location=location, out_dir=work_id, name_prefix=f"{i}-"
        )
