import os
from urllib.request import urlretrieve

import httpx


def download_digital_location(location, out_dir, name_prefix):
    """
    Download a DigitalLocation to the specified directory and prefix.
    """
    assert location["type"] == "DigitalLocation"

    # e.g. https://iiif.wellcomecollection.org/image/L1234.jpg/info.json
    if location["url"].endswith("/info.json"):
        filename = os.path.basename(
            location["url"][:-len("/info.json")]
        )
        out_path = os.path.join(out_dir, name_prefix + filename)

        urlretrieve(
            location["url"].replace("/info.json", "/full/full/0/default.jpg"),
            out_path
        )

    # e.g. https://wellcomelibrary.org/iiif/b28405717/manifest
    elif (
        location["url"].startswith("https://wellcomelibrary.org/iiif/") and
        location["url"].endswith("/manifest")
    ):
        manifest = httpx.get(location["url"]).json()

        # TODO: What if it's a multiple manifestation?
        for sequence in manifest["sequences"]:
            for canvas in sequence["canvases"]:
                for image in canvas["images"]:
                    filename = os.path.basename(image["@id"])
                    urlretrieve(
                        image["resource"]["@id"].replace("!1024,1024", "full"),
                        os.path.join(out_dir, filename)
                    )

    else:
        raise RuntimeError(f"Unrecognised URL: {location['url']}")
