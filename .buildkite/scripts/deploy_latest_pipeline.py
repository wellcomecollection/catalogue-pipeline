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


def deploy_to(root, pipeline_date):

    os.environ.update({"PIPELINE_DATE": pipeline_date})

    subprocess.check_call(
        [
            "bash",
            f"{root}/builds/deploy_catalogue_pipeline.sh",
            "tag_images_and_deploy_services",
        ]
    )


def get_pipeline_names_from_terraform_dir(root):
    tf_dir = f"{root}/pipeline/terraform"
    pipeline_date_regex = re.compile(r"^(?P<date>\d\d\d\d-\d\d-\d\d)")
    subdirectories = [
        file for file in os.listdir(tf_dir) if os.path.isdir(os.path.join(tf_dir, file))
    ]
    return [dir for dir in subdirectories if pipeline_date_regex.match(dir)]


if __name__ == "__main__":
    index_name = get_current_index_name()
    print(f"The current index name is {index_name}")

    print()
    # The works index name is a string that looks something like
    #
    #     works-indexed-2021-08-19
    #
    index_regex = re.compile(r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})[a-f]*$")
    prod_pipeline = index_regex.match(index_name).group("date")
    print(f"The current prod pipeline is {prod_pipeline}")
    print()

    root = (
        subprocess.check_output(["git", "rev-parse", "--show-toplevel"])
        .decode("utf8")
        .strip()
    )
    candidate_pipelines = get_pipeline_names_from_terraform_dir(root)
    print(f"possible existing pipelines are: {', '.join(candidate_pipelines)}")
    latest_pipeline = sorted(candidate_pipelines, reverse=True)[0]
    print(f"most recent pipeline is: {latest_pipeline}")
    if latest_pipeline != prod_pipeline:
        print(
            "WARNING: The most up to date pipeline is not the current production pipeline "
        )
        print(f"production:\t{prod_pipeline}\nlatest:\t\t{latest_pipeline}")
    deploy_to(root, latest_pipeline)
