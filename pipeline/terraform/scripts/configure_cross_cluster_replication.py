#!/usr/bin/env python
"""
This script sets up a following for the works/images indexes in the
catalogue-api cluster.  Only run this script after the initial reindex
has completed.

Usage:

    $ python3 configure_cross_cluster_replication.py 2021-06-29

"""

import sys
import time

from elasticsearch import Elasticsearch

from aws import get_session, read_secret


def get_catalogue_es_client(catalogue_session):
    secret_prefix = "elasticsearch/catalogue_api"

    es_host = read_secret(catalogue_session, secret_id=f"{secret_prefix}/public_host")
    es_protocol = read_secret(catalogue_session, secret_id=f"{secret_prefix}/protocol")
    es_port = read_secret(catalogue_session, secret_id=f"{secret_prefix}/port")

    username = read_secret(catalogue_session, secret_id=f"{secret_prefix}/username")
    password = read_secret(catalogue_session, secret_id=f"{secret_prefix}/password")

    catalogue_es = Elasticsearch(f"{es_protocol}")

    endpoint = f"{es_protocol}://{es_host}:{es_port}"

    return Elasticsearch(endpoint, http_auth=(username, password))


def get_pipeline_hostname(platform_session, *, pipeline_date):
    return read_secret(
        platform_session,
        secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/public_host",
    )


def set_remote_cluster(client, *, dst_name, src_hostname, src_name):
    """
    Set the dst cluster as a remote cluster in the src cluster.
    """
    settings = catalogue_es_client.cluster.get_settings()

    # Add the pipeline cluster as a remote cluster to the catalogue-api
    # cluster.  If it's already configured, we can skip changing the settings
    # and go straight to checking for connectivity.
    new_cluster_info = {
        "mode": "proxy",
        "proxy_address": f"{src_hostname}:9400",
        "server_name": src_hostname,
        "skip_unavailable": "false",
    }

    if settings["persistent"]["cluster"]["remote"].get(src_name) != new_cluster_info:
        settings["persistent"]["cluster"]["remote"][src_name] = new_cluster_info
        catalogue_es_client.cluster.put_settings(settings)

    # Wait up to 10 seconds to see if the catalogue-api cluster can connect
    # to the pipeline cluster.  As soon as we see a successful connection we
    # can continue; if we don't then something has gone wrong.
    now = time.time()
    while time.time() - now < 10:
        remote_info = catalogue_es_client.cluster.remote_info()

        if remote_info.get(src_name, {}).get("connected"):
            return

    raise RuntimeError(
        f"{dst_name} cluster could not connect to {src_name} as a remote cluster! "
        "Check the trust settings in {src_name}, delete the remote cluster, then try again."
    )


def create_follower_indexes(client, *, src_name, pipeline_date):
    for prefix in ("works-indexed", "images-indexed"):
        index = f"{prefix}-{pipeline_date}"
        client.ccr.follow(
            index, body={"remote_cluster": src_name, "leader_index": index}
        )


if __name__ == "__main__":
    try:
        pipeline_date = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <PIPELINE_DATE>")

    platform_session = get_session(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )
    catalogue_session = get_session(
        role_arn="arn:aws:iam::756629837203:role/catalogue-developer"
    )

    pipeline_hostname = get_pipeline_hostname(
        platform_session, pipeline_date=pipeline_date
    )

    catalogue_es_client = get_catalogue_es_client(catalogue_session)

    set_remote_cluster(
        catalogue_es_client,
        dst_name="catalogue-api",
        src_hostname=pipeline_hostname,
        src_name=f"pipeline-{pipeline_date}",
    )

    create_follower_indexes(
        catalogue_es_client,
        src_name=f"pipeline-{pipeline_date}",
        pipeline_date=pipeline_date
    )
