#!/usr/bin/env python
"""
Reports some stats about the state of a reindex.
"""

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


def get_pipeline_storage_es_client(session, *, reindex_date):
    """
    Returns an Elasticsearch client for the pipeline-storage cluster.
    """
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

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


def get_api_es_client(session):
    """
    Returns an Elasticsearch client for the catalogue cluster.
    """
    host = get_secret_string(session, secret_id="catalogue/api/es_host")
    port = get_secret_string(session, secret_id="catalogue/api/es_port")
    protocol = get_secret_string(session, secret_id="catalogue/api/es_protocol")
    username = get_secret_string(session, secret_id="catalogue/api/es_username")
    password = get_secret_string(session, secret_id="catalogue/api/es_password")

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
def count_documents_in_index(es_client, *, index_name):
    """
    Returns the number of documents in an Elasticsearch index.
    """
    try:
        count_resp = es_client.cat.count(index_name, format="json")
    except NotFoundError:
        return 0
    else:
        assert len(count_resp) == 1, (index_name, count_resp)
        return int(count_resp[0]["count"])


def get_works_index_stats(session, *, reindex_date):
    """
    Returns a map (step) -> (ES documents count).
    """
    pipeline_client = get_pipeline_storage_es_client(session, reindex_date=reindex_date)

    indexes = ["works-source", "works-identified", "works-merged", "works-denormalised"]

    result = {
        idx: count_documents_in_index(
            pipeline_client, index_name=f"{idx}-{reindex_date}"
        )
        for idx in indexes
    }

    api_client = get_api_es_client(session)
    result["API"] = count_documents_in_index(
        api_client, index_name=f"works-{reindex_date}"
    )

    return result


def get_images_index_stats(session, *, reindex_date):
    """
    Returns a map (step) -> (ES documents count).
    """
    pipeline_client = get_pipeline_storage_es_client(session, reindex_date=reindex_date)

    indexes = ["images-initial", "images-augmented"]

    result = {
        idx: count_documents_in_index(
            pipeline_client, index_name=f"{idx}-{reindex_date}"
        )
        for idx in indexes
    }

    api_client = get_api_es_client(session)
    result["API"] = count_documents_in_index(
        api_client, index_name=f"images-{reindex_date}"
    )

    return result


@click.command()
@click.argument("reindex_date")
def main(reindex_date):
    session_read_only = get_session_with_role(
        "arn:aws:iam::760097843905:role/platform-read_only"
    )
    session_dev = get_session_with_role(
        "arn:aws:iam::760097843905:role/platform-developer"
    )

    source_counts = get_source_counts(session_read_only)
    source_counts["TOTAL"] = sum(source_counts.values())

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

    work_index_stats = get_works_index_stats(session_dev, reindex_date=reindex_date)

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

    image_index_stats = get_images_index_stats(session_dev, reindex_date=reindex_date)

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

    proportion = work_index_stats["API"] / source_counts["TOTAL"] * 100
    if proportion < 99:
        print(
            click.style(
                "Approximately %d%% of records have been reindexed successfully"
                % int(proportion),
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
