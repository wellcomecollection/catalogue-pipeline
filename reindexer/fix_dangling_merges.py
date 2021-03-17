#!/usr/bin/env python
"""
A "dangling merge" is when the redirect information in the API index
isn't consistent.  There are two variants:

1)  Work A redirects to Work B, but the copy of Work B in the index doesn't
    have the merged data from Work A

2)  Work B has the merged data from Work A, but Work A doesn't redirect to B

This script will scan the API index, find any dangling merges, and offer
to redrive these messages through the pipeline.

Ideally we wouldn't need this, and hopefully at some point the pipeline
will become reliable enough that we can delete it.  In the meantime, this
should keep reindexes consistent, and perhaps give us some clues about
where this problem is coming from.

"""

import collections
import datetime
import json
import sys

import click
from elasticsearch.helpers import scan
import humanize
import tqdm

from get_reindex_status import get_api_es_client, get_session_with_role
from pipeline_inject_messages import inject_id_messages


def get_works(reindex_date):
    session = get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )
    es_client = get_api_es_client(session)

    for work in scan(
        es_client,
        index=f"works-{reindex_date}",
        query={
            "_source": [
                "state.canonicalId",
                "redirectSources.canonicalId",
                "redirectTarget.canonicalId",
                "type",
            ]
        },
    ):
        yield work["_source"]


if __name__ == "__main__":
    try:
        reindex_date = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <REINDEX_DATE>")

    session = get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )
    es_client = get_api_es_client(session)

    works = {}

    for w in tqdm.tqdm(get_works(reindex_date)):
        canonical_id = w["state"]["canonicalId"]

        works[canonical_id] = {
            "redirectSources": [
                rs["canonicalId"] for rs in w.get("redirectSources", [])
            ],
            "redirectTarget": w.get("redirectTarget", {}).get("canonicalId"),
            "type": w["type"],
        }

    errors = collections.defaultdict(list)
    affected_work_ids = set()

    for work_id, w in works.items():
        for redirect_source_id in w["redirectSources"]:
            if works[redirect_source_id]["redirectTarget"] != work_id:
                errors[work_id].append(
                    f"redirect source {redirect_source_id} is actually redirecting to {works[redirect_source_id]['redirectTarget']}"
                )
                affected_work_ids.add(work_id)
                affected_work_ids.add(redirect_source_id)

        if w["redirectTarget"]:
            try:
                target_work = works[w["redirectTarget"]]
            except KeyError:
                errors[work_id].append(
                    f"redirects to {w['redirectTarget']}, which does not exist"
                )
                affected_work_ids.add(w["redirectTarget"])
                affected_work_ids.add(work_id)
            else:
                if work_id not in target_work["redirectSources"]:
                    errors[work_id].append(
                        f"redirects to {w['redirectTarget']}, but is not listed in its redirect sources ({works[w['redirectTarget']]['redirectSources']})"
                    )
                    affected_work_ids.add(work_id)
                    affected_work_ids.add(w["redirectTarget"])

    if errors:
        with open(
            f"errors-{reindex_date}-{datetime.datetime.now()}.json", "w"
        ) as outfile:
            outfile.write(
                json.dumps(
                    {"errors": errors, "affected_work_ids": sorted(affected_work_ids)},
                    indent=2,
                )
            )

        total_errors = sum(len(v) for v in errors.values())
        print(
            click.style(
                "Detected %s dangling redirect error%s"
                % (humanize.intcomma(total_errors), "s" if total_errors > 1 else ""),
                "red",
            )
        )

        if click.confirm("Resend these works to the merger?"):
            inject_id_messages(
                session,
                destination_name="id_minter_output",
                reindex_date=reindex_date,
                ids=affected_work_ids,
            )

    else:
        print(click.style("No errors detected!", "green"))
