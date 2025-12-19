import argparse
import typing

from models.events import BasePipelineEvent
from utils.elasticsearch import ElasticsearchMode, get_client, get_merged_index_name


def handler(event: BasePipelineEvent, es_mode: ElasticsearchMode = "private") -> dict:
    """Create a point in time (PIT) on the merged index and return its PIT ID."""
    es_client = get_client("graph_extractor", event.pipeline_date, es_mode)

    index_name = get_merged_index_name(event)

    pit = es_client.open_point_in_time(index=index_name, keep_alive="15m")

    return {"pit_id": pit["id"]}


def lambda_handler(event: dict, context: typing.Any) -> dict:
    return handler(BasePipelineEvent(**event))


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help='The pipeline date of the Elasticsearch cluster to connect to, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--es-mode",
        type=str,
        help="Which Elasticsearch cluster to connect to. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["local", "public"],
        default="local",
    )

    args = parser.parse_args()
    event = BasePipelineEvent(**args.__dict__)

    print(handler(event, es_mode=args.es_mode))


if __name__ == "__main__":
    local_handler()
