#!/usr/bin/env python
"""
The API diff tool will tell you how many works are Visible, Redirected,
Invisible, and Deleted.

Unfortunately, these numbers aren't always consistent, and sometimes these
numbers change in unexpected ways.

This script will scan the prod and staging indexes, and redrive any Work
with a different type to *both* pipelines.  Historically, this seems to
make them a bit more consistent.

Ideally we wouldn't need this, and hopefully at some point the pipeline
will become reliable enough that we can delete it.  In the meantime, this
should keep reindexes consistent, and perhaps give us some clues about
where this problem is coming from.
"""

import json

from elasticsearch import Elasticsearch
from elasticsearch.helpers import scan
import tqdm

from api_stats import get_session_with_role, get_api_es_url, get_index_name


def get_works(es_client, *, index_name):
    for work in tqdm.tqdm(scan(es_client, index=index_name, query={"_source": "type"})):
        yield (work["_id"], work["_source"]["type"])


if __name__ == "__main__":
    session = get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )

    es_url = get_api_es_url(session)
    es_client = Elasticsearch(es_url)

    print("Fetching works from the prod index...")
    prod_index_name = get_index_name("api.wellcomecollection.org")
    prod_works = set(get_works(es_client, index_name=prod_index_name))

    print("Fetching works from the stage index...")
    stage_index_name = get_index_name("api.wellcomecollection.org")
    stage_works = set(get_works(es_client, index_name=stage_index_name))

    mismatched_work_ids = set(
        work_id for work_id, _ in prod_works.symmetric_difference(stage_works)
    )

    out_path = f"mismatched_work_ids--{prod_index_name}--{stage_index_name}--{datetime.datetime.now()}.json"

    with open(out_path, "w") as outfile:
        outfile.write(json.dumps(sorted(mismatched_work_ids)))

    print(f"Written results to {out_path}")

    print(f"Found %d mismatched work IDs; redriving..." % len(mismatched_work_ids))
    sns = session.client("sns")

    prod_index_date = prod_index_name.replace("works-", "")
    stage_index_date = stage_index_name.replace("works-")

    for work_id in tqdm.tqdm(mismatched_work_ids):
        sns.publish(
            TopicArn=f"arn:aws:sns:eu-west-1:760097843905:catalogue-{prod_index_date}_id_minter_output",
            Body=work_id,
        )
        sns.publish(
            TopicArn=f"arn:aws:sns:eu-west-1:760097843905:catalogue-{stage_index_date}_id_minter_output",
            Body=work_id,
        )
