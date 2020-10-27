import os
from urllib.request import urlretrieve


def download_digital_location(location, out_dir, name_prefix):
    """
    Download a DigitalLocation to the specified directory and prefix.
    """
    assert location["type"] == "DigitalLocation"

    if location["url"].endswith("/info.json"):
        filename = os.path.basename(
            location["url"][:-len("/info.json")]
        )
        out_path = os.path.join(out_dir, name_prefix + filename)

        urlretrieve(
            location["url"].replace("/info.json", "/full/full/0/default.jpg"),
            out_path
        )
    else:
        raise RuntimeError(f"Unrecognised URL: {location['url']}")
