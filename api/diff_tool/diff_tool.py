#!/bin/env python3

import datetime
import difflib
import json
import os
import tempfile
import urllib.parse

import click
from jinja2 import Environment, FileSystemLoader, select_autoescape
import requests
import tqdm


class ApiDiffer:
    """Performs a diff against the same call to both prod and stage works API,
    printing the results to stdout.
    """

    prod = "api.wellcomecollection.org"
    stage = "api-stage.wellcomecollection.org"

    def __init__(self, work_id=None, params=None):
        suffix = f"/{work_id}" if work_id else ""
        self.path = f"/catalogue/v2/works{suffix}"
        self.params = params or {}

    @property
    def display_url(self):
        display_params = urllib.parse.urlencode(list(self.params.items()))
        if display_params:
            return f"{self.path}?{display_params}"
        else:
            return self.path

    def get_html_diff(self):
        """
        Fetches a URL from the prod/staging API, and returns a (status, HTML diff).
        """
        (prod_status, prod_json) = self.call_api(self.prod)
        (stage_status, stage_json) = self.call_api(self.stage)
        if prod_status != stage_status:
            lines = [
                f"* Received {prod_status} on prod and {stage_status} on stage",
                "",
                "prod:",
                f"{json.dumps(prod_json, indent=2)}",
                "",
                "stage:",
                f"{json.dumps(stage_json, indent=2)}",
            ]
            return ("different status", "\n".join(lines))
        elif prod_json == stage_json:
            return ("match", "")
        else:
            prod_pretty = json.dumps(prod_json, indent=2, sort_keys=True)
            stage_pretty = json.dumps(stage_json, indent=2, sort_keys=True)

            return (
                "different JSON",
                list(
                    difflib.unified_diff(
                        prod_pretty.splitlines(),
                        stage_pretty.splitlines(),
                        fromfile="prod",
                        tofile="stage",
                    )
                ),
            )

    def call_api(self, api_base):
        url = f"https://{api_base}{self.path}"
        response = requests.get(url, params=self.params)
        return (response.status_code, response.json())


@click.command()
@click.option(
    "--routes-file",
    default="routes.json",
    help="What routes file to use (default=routes.json)",
)
def main(routes_file):
    with open(routes_file) as f:
        routes = json.load(f)

    diffs = []

    for route in tqdm.tqdm(routes):
        work_id, params = route.get("workId"), route.get("params")
        differ = ApiDiffer(work_id, params)
        status, diff_lines = differ.get_html_diff()

        diffs.append(
            {
                "display_url": differ.display_url,
                "status": status,
                "diff_lines": diff_lines,
            }
        )

    env = Environment(
        loader=FileSystemLoader("."), autoescape=select_autoescape(["html", "xml"])
    )

    template = env.get_template("template.html")
    html = template.render(now=datetime.datetime.now(), diffs=diffs)

    _, tmp_path = tempfile.mkstemp(suffix=".html")
    with open(tmp_path, "w") as outfile:
        outfile.write(html)

    os.system(f"open {tmp_path}")


if __name__ == "__main__":
    main()
