#!/usr/bin/env python
"""
This is a script for comparing the API responses for a work in the TEI-off/
TEI-on pipelines.

This will help us identify where we need to be making changes to
the TEI transformation process.
"""

import difflib
import json
import sys

import httpx

if __name__ == "__main__":
    try:
        work_id = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <WORK_ID>")

    client = httpx.Client(base_url="https://api.wellcomecollection.org/catalogue/v2")

    works_index = client.get("/_elasticConfig").json()["worksIndex"]
    
    includes = client.get("/swagger.json").json()["paths"]["/works/{id}"]["get"][
        "parameters"
    ][1]["schema"]["enum"]

    tei_off_work = client.get(
        f"/works/{work_id}",
        params={"includes": ",".join(includes), "_index": works_index},
    ).json()

    tei_on_work = client.get(
        f"/works/{work_id}",
        params={"includes": ",".join(includes), "_index": f"{works_index}-tei-on"},
    ).json()

    diff = difflib.HtmlDiff()

    print(
        diff.make_file(
            fromlines=json.dumps(tei_off_work, indent=2, sort_keys=True).splitlines(),
            tolines=json.dumps(tei_on_work, indent=2, sort_keys=True).splitlines(),
            fromdesc="pre-TEI work",
            todesc="work in the TEI pipeline",
        )
        .replace(
            '<style type="text/css">',
            """
        <style type="text/css">
            td.diff_content {
                max-width: 750px;
                word-wrap: break-word;
                padding-right: 5px;
            }

            span.diff_sub {
                max-width: 750px;
            }
        """,
        )
        .replace('<td nowrap="nowrap">', '<td class="diff_content">')
    )

    # print(tei_off_work)
    # print(tei_on_work)
