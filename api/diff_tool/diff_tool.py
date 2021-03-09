#!/usr/bin/env python3

import concurrent.futures
import datetime
import difflib
import json
import os
import tempfile
import urllib.parse

import click
import humanize
from jinja2 import Environment, FileSystemLoader, select_autoescape
import requests

import api_stats


PROD_URL = "api.wellcomecollection.org"
STAGING_URL = "api-stage.wellcomecollection.org"


class ApiDiffer:
    """Performs a diff against the same call to both prod and stage works API,
    printing the results to stdout.
    """

    def __init__(self, path=None, params=None, **kwargs):
        self.path = f"/catalogue/v2{path}"
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
        (prod_status, prod_json) = self.call_api(PROD_URL)
        (stage_status, stage_json) = self.call_api(STAGING_URL)
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
            return ("different status", lines)
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
    session = api_stats.get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )

    with open(routes_file) as f:
        routes = json.load(f)

    def get_diff(route):
        differ = ApiDiffer(**route)
        status, diff_lines = differ.get_html_diff()

        return {
            "route": route,
            "display_url": differ.display_url,
            "status": status,
            "diff_lines": diff_lines,
        }

    with concurrent.futures.ThreadPoolExecutor() as executor:
        futures = [executor.submit(get_diff, r) for r in routes]
        concurrent.futures.wait(futures, return_when=concurrent.futures.ALL_COMPLETED)

        diffs = [fut.result() for fut in futures]

    stats = {
        label: api_stats.get_api_stats(session, api_url=api_url)
        for (label, api_url) in [("prod", PROD_URL), ("staging", STAGING_URL)]
    }

    env = Environment(
        loader=FileSystemLoader("."), autoescape=select_autoescape(["html", "xml"])
    )

    env.filters["intcomma"] = humanize.intcomma

    template = env.get_template("template.html")
    html = template.render(now=datetime.datetime.now(), diffs=diffs, stats=stats)

    _, tmp_path = tempfile.mkstemp(suffix=".html")
    with open(tmp_path, "w") as outfile:
        outfile.write(html)

    os.system(f"open {tmp_path}")


if __name__ == "__main__":
    main()
