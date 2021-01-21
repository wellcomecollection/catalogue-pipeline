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


def get_pipeline_storage_es_client(session, *, deployment_id):
    """
    Returns an Elasticsearch client for the pipeline-storage cluster.
    """
    host = get_secret_string(session, secret_id=f"catalogue/{deployment_id}/es_host")
    port = get_secret_string(session, secret_id=f"catalogue/{deployment_id}/es_port")
    protocol = get_secret_string(
        session, secret_id=f"catalogue/{deployment_id}/es_protocol"
    )
    username = get_secret_string(
        session, secret_id=f"catalogue/{deployment_id}/dev/es_username"
    )
    password = get_secret_string(
        session, secret_id=f"catalogue/{deployment_id}/dev/es_password"
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


def get_index_stats(session, *, deployment_id, reindex_date):
    """
    Returns a map (step) -> (ES documents count).
    """
    pipeline_client = get_pipeline_storage_es_client(
        session, deployment_id=deployment_id
    )

    pipeline_steps = ["source", "identified", "merged", "denormalised"]
    result = {}

    for step in pipeline_steps:
        result[f"works-{step}"] = count_documents_in_index(
            pipeline_client, index_name=f"works-{step}-{reindex_date}"
        )

    api_client = get_api_es_client(session)
    result["API"] = count_documents_in_index(
        api_client, index_name=f"works-{reindex_date}"
    )

    return result


@click.command()
@click.option("--deployment-id", default="pipeline_storage")
@click.argument("reindex_date")
def main(reindex_date, deployment_id):
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

    index_stats = get_index_stats(
        session_dev, deployment_id=deployment_id, reindex_date=reindex_date
    )

    rows = [[step, count] for step, count in index_stats.items()]
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

    print("*** Work index stats ***")
    print(tabulate.tabulate(rows, tablefmt="plain", colalign=("left", "right")))

    print("")

    proportion = index_stats["API"] / source_counts["TOTAL"] * 100
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
