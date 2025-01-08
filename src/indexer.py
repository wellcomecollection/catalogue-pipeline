import argparse
import json
import typing

from utils.aws import get_neptune_client


def extract_sns_messages_from_sqs_event(event: dict) -> list[str]:
    queries = []

    for record in event["Records"]:
        query = json.loads(record["body"])["Message"]
        queries.append(query)

    return queries


def handler(queries: list[str], is_local: bool = False) -> None:
    neptune_client = get_neptune_client(is_local)

    print(f"Received number of queries: {len(queries)}")

    for query in queries:
        neptune_client.run_open_cypher_query(query)


def lambda_handler(event: dict, context: typing.Any) -> None:
    queries = extract_sns_messages_from_sqs_event(event)
    handler(queries)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--cypher-query",
        type=str,
        help="An openCypher query to run against the Neptune cluster.",
        required=True,
    )
    args = parser.parse_args()

    handler([args.cypher_query], is_local=True)


if __name__ == "__main__":
    local_handler()
