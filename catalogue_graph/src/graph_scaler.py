import argparse
import math
import time
import typing

import boto3

import config


def scale_cluster(min_capacity: float, max_capacity: float) -> None:
    client = boto3.client("neptune")
    client.modify_db_cluster(
        DBClusterIdentifier=config.NEPTUNE_CLUSTER_IDENTIFIER,
        ServerlessV2ScalingConfiguration={
            "MinCapacity": min_capacity,
            "MaxCapacity": max_capacity,
        },
        ApplyImmediately=True,
    )


def check_cluster_ncus() -> dict:
    client = boto3.client("neptune")
    response = client.describe_db_clusters(
        DBClusterIdentifier=config.NEPTUNE_CLUSTER_IDENTIFIER,
    )

    cluster_info = response["DBClusters"][0]
    scaling_config = cluster_info["ServerlessV2ScalingConfiguration"]

    return {
        "min_capacity": scaling_config["MinCapacity"],
        "max_capacity": scaling_config["MaxCapacity"],
        "status": cluster_info["Status"],
    }


def handler(min_capacity: float, max_capacity: float) -> None:
    curr_capacity = check_cluster_ncus()
    print(f"Current minimum capacity: {curr_capacity['min_capacity']}.")
    print(f"Current maximum capacity: {curr_capacity['max_capacity']}.")

    scale_cluster(min_capacity, max_capacity)

    while not (
        math.isclose(curr_capacity["min_capacity"], min_capacity)
        and math.isclose(curr_capacity["max_capacity"], max_capacity)
        and curr_capacity["status"] == "available"
    ):
        time.sleep(30)
        curr_capacity = check_cluster_ncus()
        print(curr_capacity)

    print(f"Successfully scaled minimum capacity to {min_capacity}.")
    print(f"Successfully scaled maximum capacity to {max_capacity}.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    min_capacity = event["min_capacity"]
    max_capacity = event["max_capacity"]
    handler(min_capacity, max_capacity)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--min-capacity",
        type=float,
        help="The minimum NCU capacity of the serverless cluster.",
        required=True,
    )
    parser.add_argument(
        "--max-capacity",
        type=float,
        help="The maximum NCU capacity of the serverless cluster.",
        required=True,
    )

    args = parser.parse_args()

    handler(**args.__dict__)


if __name__ == "__main__":
    local_handler()
