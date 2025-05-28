import typing

import boto3
import config


def check_cluster_status() -> str:
    client = boto3.client("neptune")
    response = client.describe_db_clusters(
        DBClusterIdentifier=config.NEPTUNE_CLUSTER_IDENTIFIER,
    )

    cluster_info = response["DBClusters"][0]
    return cluster_info["Status"]


def handler() -> str:
    return {"status": check_cluster_status()}


def lambda_handler(event: dict, context: typing.Any) -> str:
    return handler()


if __name__ == "__main__":
    print(handler())
