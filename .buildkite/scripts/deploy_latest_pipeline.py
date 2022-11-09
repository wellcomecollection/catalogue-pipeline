#!/usr/bin/env python3

import os
import re
import subprocess

import httpx


def get_current_index_name():
    """
    Returns index which is currently being served by the API.
    """
    resp = httpx.get("https://api.wellcomecollection.org/catalogue/v2/_elasticConfig")
    resp.raise_for_status()
    return resp.json()["worksIndex"]


if __name__ == "__main__":
    index_name = get_current_index_name()
    print(f"The current index name is {index_name}")

    print()

    # The works index name is a string that looks something like
    #
    #     works-indexed-2021-08-19
    #
    index_regex = re.compile(r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})[a-f]*$")
    pipeline_date = index_regex.match(index_name).group("date")
    print(f"The current prod pipeline is {pipeline_date}")

    print()

    root = (
        subprocess.check_output(["git", "rev-parse", "--show-toplevel"])
        .decode("utf8")
        .strip()
    )

    os.environ.update({"PIPELINE_DATE": pipeline_date})

    subprocess.check_call(
        [
            "bash",
            f"{root}/builds/deploy_catalogue_pipeline.sh",
            "tag_images_and_deploy_services",
        ],
    )
