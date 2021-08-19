#!/usr/bin/env python3

import json
import re
import urllib.request


def get_internal_model_version():
    with open("project/Dependencies.scala") as infile:
        internal_model_line = next(
            line for line in infile if line.strip().startswith("val internalModel =")
        )

        return internal_model_line.split()[-1].strip('"')


if __name__ == "__main__":
    resp = json.load(
        urllib.request.urlopen(
            "https://api.wellcomecollection.org/catalogue/v2/_elasticConfig"
        )
    )

    works_index = resp["worksIndex"]
    pipeline_date = re.match(
        r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})$", works_index
    ).group("date")
    print(f"The current prod pipeline is {pipeline_date}")

    internal_model_version = get_internal_model_version()
    print(f"The current version of internal model is {internal_model_version}")
