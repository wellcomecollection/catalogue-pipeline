import argparse
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


def handler(min_capacity: float, max_capacity: float) -> None:
    scale_cluster(min_capacity, max_capacity)


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
