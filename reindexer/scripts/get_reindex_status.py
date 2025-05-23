#!/usr/bin/env python3
"""
Reports some stats about the state of a reindex.
"""

import datetime
import functools

import boto3
import click
from elasticsearch import Elasticsearch
from elasticsearch.exceptions import NotFoundError
import humanize
import tabulate
from tenacity import retry, stop_after_attempt, wait_exponential


def get_session_with_role(role_arn):
    """
    Returns a boto3.Session that uses the given role ARN.
    """
    sts_client = boto3.client("sts")

    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.Session(
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def count_items_in_table(session, *, table_name):
    """
    Returns an approximate number of items in a table.
    """
    dynamodb = session.client("dynamodb")

    return dynamodb.describe_table(TableName=table_name)["Table"]["ItemCount"]


def get_source_counts(session):
    """
    Returns a map (source name) -> (item count).
    """
    source_tables = {
        "calm": "vhs-calm-adapter",
        "mets": "mets-adapter-store-delta",
        "miro": "vhs-sourcedata-miro",
        "sierra": "vhs-sierra-sierra-adapter-20200604",
        "tei": "tei-adapter-store",
    }

    return {
        name: count_items_in_table(session, table_name=table_name)
        for name, table_name in source_tables.items()
    }


def get_secret_string(session, *, secret_id):
    """
    Look up the value of a SecretString in Secrets Manager.
    """
    secrets = session.client("secretsmanager")
    return secrets.get_secret_value(SecretId=secret_id)["SecretString"]


@functools.lru_cache()
def get_pipeline_storage_es_client(reindex_date):
    """
    Returns an Elasticsearch client for the pipeline-storage cluster.
    """
    session = get_session_with_role("arn:aws:iam::760097843905:role/platform-developer")

    secret_prefix = f"elasticsearch/pipeline_storage_{reindex_date}"

    host = get_secret_string(session, secret_id=f"{secret_prefix}/public_host")
    port = get_secret_string(session, secret_id=f"{secret_prefix}/port")
    protocol = get_secret_string(session, secret_id=f"{secret_prefix}/protocol")
    username = get_secret_string(
        session, secret_id=f"{secret_prefix}/read_only/es_username"
    )
    password = get_secret_string(
        session, secret_id=f"{secret_prefix}/read_only/es_password"
    )
    return Elasticsearch(f"{protocol}://{host}:{port}", basic_auth=(username, password))


@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
def count_documents_in_index(es_client, *, index_name):
    """
    Returns the number of documents in an Elasticsearch index.
    """
    try:
        count_resp = es_client.cat.count(index=index_name, format="json")
    except NotFoundError:
        return 0
    else:
        assert len(count_resp) == 1, (index_name, count_resp)
        return int(count_resp[0]["count"])


def get_works_index_stats(*, reindex_date):
    """
    Returns a map (step) -> (ES documents count).
    """
    pipeline_client = get_pipeline_storage_es_client(reindex_date=reindex_date)

    indexes = [
        "works-source",
        "works-identified",
        "works-denormalised",
        "works-indexed",
    ]

    result = {
        idx: count_documents_in_index(
            pipeline_client, index_name=f"{idx}-{reindex_date}"
        )
        for idx in indexes
    }

    return result


def get_images_index_stats(*, reindex_date):
    """
    Returns a map (step) -> (ES documents count).
    """
    pipeline_client = get_pipeline_storage_es_client(reindex_date=reindex_date)

    indexes = ["images-initial", "images-augmented", "images-indexed"]

    result = {
        idx: count_documents_in_index(
            pipeline_client, index_name=f"{idx}-{reindex_date}"
        )
        for idx in indexes
    }

    return result


def list_queue_urls_in_account(sess, *, prefixes):
    """
    Generates a list of all the queue URLs in an account.
    """
    sqs_client = sess.client("sqs")

    for prefix in prefixes:
        for page in sqs_client.get_paginator("list_queues").paginate(
            QueueNamePrefix=prefix
        ):
            yield from page["QueueUrls"]


def get_queue_stats(sess, *, reindex_date):
    """
    Get the size of the queues associated with this pipeline.
    """
    queue_urls = list_queue_urls_in_account(
        sess, prefixes=(f"catalogue-{reindex_date}", "reindex")
    )

    sqs_client = sess.client("sqs")

    attribute_names = [
        "ApproximateNumberOfMessages",
        "ApproximateNumberOfMessagesNotVisible",
        "ApproximateNumberOfMessagesDelayed",
    ]

    queue_responses = {
        q_url: sqs_client.get_queue_attributes(
            QueueUrl=q_url, AttributeNames=attribute_names
        )
        for q_url in queue_urls
    }

    return {
        q_url: sum(int(resp["Attributes"][attr]) for attr in attribute_names)
        for q_url, resp in queue_responses.items()
    }


@click.command()
@click.argument("reindex_date")
def main(reindex_date):
    session_read_only = get_session_with_role(
        "arn:aws:iam::760097843905:role/platform-read_only"
    )

    source_counts = get_source_counts(session_read_only)
    source_counts["TOTAL"] = sum(source_counts.values())
    print("*** Status check time *** ")
    print(datetime.datetime.now())
    print("")

    print("*** Source tables ***")
    print(
        tabulate.tabulate(
            [(name, humanize.intcomma(count)) for name, count in source_counts.items()],
            tablefmt="plain",
            colalign=("left", "right"),
        )
    )

    print("")

    print("*** Work index stats ***")

    work_index_stats = get_works_index_stats(reindex_date=reindex_date)

    rows = [[step, count] for step, count in work_index_stats.items()]
    rows.insert(0, ["source records", source_counts["TOTAL"], ""])

    for idx, r in enumerate(rows[1:], start=1):
        this_count = r[1]
        prev_count = rows[idx - 1][1]
        if prev_count > this_count:
            diff = prev_count - this_count
            r.append(click.style(f"▼ {humanize.intcomma(diff)}", "red"))
        else:
            r.append("")

    rows = [(name, humanize.intcomma(count), diff) for (name, count, diff) in rows]

    print(tabulate.tabulate(rows, tablefmt="plain", colalign=("left", "right")))

    print("")

    print("*** Image index stats ***")

    image_index_stats = get_images_index_stats(reindex_date=reindex_date)

    rows = [[step, count] for step, count in image_index_stats.items()]
    rows[0].append("")

    for idx, r in enumerate(rows[1:], start=1):
        this_count = r[1]
        prev_count = rows[idx - 1][1]
        if prev_count > this_count:
            diff = prev_count - this_count
            r.append(click.style(f"▼ {humanize.intcomma(diff)}", "red"))
        else:
            r.append("")

    rows = [(name, humanize.intcomma(count), diff) for (name, count, diff) in rows]

    print(tabulate.tabulate(rows, tablefmt="plain", colalign=("left", "right")))

    print("")

    queue_stats = get_queue_stats(session_read_only, reindex_date=reindex_date)

    print("*** SQS stats ***")
    non_empty_queues = {
        q_url: size
        for q_url, size in queue_stats.items()
        if not q_url.endswith("_dlq") and size > 0
    }
    if non_empty_queues:
        print("The following queues still have messages:")
        rows = [
            [
                q_url.split("/")[-1].replace(f"catalogue-{reindex_date}_", ""),
                humanize.intcomma(size),
            ]
            for q_url, size in sorted(non_empty_queues.items())
        ]
        print(tabulate.tabulate(rows, tablefmt="plain", colalign=("left", "right")))
    else:
        print("There are no messages on queues")

    print("")

    print("*** DLQ stats ***")
    non_empty_dlqs = {
        q_url: size
        for q_url, size in queue_stats.items()
        if q_url.endswith("_dlq") and size > 0
    }
    if non_empty_dlqs:
        print("The following DLQs have failed messages:")
        rows = [
            [
                q_url.split("/")[-1].replace(f"catalogue-{reindex_date}_", ""),
                humanize.intcomma(size),
            ]
            for q_url, size in sorted(non_empty_dlqs.items())
        ]
        print(tabulate.tabulate(rows, tablefmt="plain", colalign=("left", "right")))
    else:
        print("There are no messages on DLQs")

    print("")

    proportion = work_index_stats["works-indexed"] / source_counts["TOTAL"] * 100
    if proportion < 99:
        print(
            click.style(
                "Approximately %d%% of records have been reindexed successfully"
                % int(proportion),
                "yellow",
            )
        )

        if work_index_stats["works-indexed"] == 0:
            print(
                click.style(
                    "(This may be because the ingestors have refresh_interval=-1 while reindexing, to reduce load on the Elasticsearch cluster)",
                    "yellow",
                )
            )

    else:
        print(
            click.style(
                "Approximately %d%% of records have been reindexed successfully (counts may not be exact)"
                % int(proportion),
                "green",
            )
        )


if __name__ == "__main__":
    main()
