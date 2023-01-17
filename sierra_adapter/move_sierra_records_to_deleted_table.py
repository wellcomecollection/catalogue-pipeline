#!/usr/bin/env python
"""
Move Sierra records that were deleted before 1 Jan 2018 to a separate
DynamoDB adapter table.

(1 Jan 2018 is about when Sierra records started to appear in the
Catalogue API, and thus might be referred to in public URLs.)

This is currently a one-off script, but we might consider extending it
to move records from within the lifetime of the Catalogue API later.

See https://github.com/wellcomecollection/docs/pull/64
"""

import boto3
from elasticsearch.helpers import scan
import more_itertools
import tqdm
from weco_datascience.reporting import get_es_client


def get_deleted_sierra_bibs(es):
    for item in scan(
        es,
        index="sierra_bibs",
        scroll="1m",
        query={
            "query": {
                "bool": {
                    "filter": [
                        {"term": {"deleted": True}},
                        {"range": {"deletedDate": {"lte": "2017-12-31"}}},
                    ]
                }
            },
            "_source": False,
        },
    ):
        yield item["_id"]


if __name__ == "__main__":
    es = get_es_client()

    session = boto3.Session()

    dynamodb = boto3.resource("dynamodb").meta.client

    live_table = "vhs-sierra-sierra-adapter-20200604"
    deleted_table = "vhs-sierra-sierra-adapter-20200604-deleted"

    for batch in more_itertools.chunked(tqdm.tqdm(get_deleted_sierra_bibs(es)), 25):
        items = dynamodb.batch_get_item(
            RequestItems={live_table: {"Keys": [{"id": bib_id} for bib_id in batch]}}
        )["Responses"][live_table]

        # If we've already moved all of the IDs in this batch.
        if not items:
            continue

        # We write the items to the "deleted" table before removing them
        # from the "live" table.  That way, if something goes wrong, we're
        # never without at least one copy of the item.
        dynamodb.batch_write_item(
            RequestItems={deleted_table: [{"PutRequest": {"Item": it}} for it in items]}
        )

        dynamodb.batch_write_item(
            RequestItems={
                live_table: [
                    {"DeleteRequest": {"Key": {"id": it["id"]}}} for it in items
                ]
            }
        )
