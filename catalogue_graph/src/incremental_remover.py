import argparse
import typing
from collections.abc import Iterator
from itertools import batched

from models.events import IncrementalRemoverEvent
from sources.merged_works_source import MergedWorksSource
from utils.aws import get_neptune_client
from utils.types import IngestorType

ES_QUERY = {"bool": {"must_not": {"match": {"type": "Visible"}}}}


def get_unused_concept_ids() -> Iterator[str]:
    """Remove the IDs of all concept nodes which are not connected to any works"""
    query = """
        MATCH (c: Concept)
        WHERE NOT (c)<-[:HAS_CONCEPT]->()
        RETURN id(c) AS id
    """

    neptune_client = get_neptune_client(True)
    result = neptune_client.time_open_cypher_query(query, {}, "unused concepts")

    for item in result:
        yield item["id"]


def get_non_visible_work_ids(
    event: IncrementalRemoverEvent, is_local: bool
) -> Iterator[str]:
    """Return the ids of all works which are not 'Visible' and which were modified within the specified time window."""
    es_source = MergedWorksSource(
        event,
        query=ES_QUERY,
        fields=["state.canonicalId"],
        es_mode="public" if is_local else "private",
    )

    for work in es_source.stream_raw():
        yield work["state"]["canonicalId"]


def handler(event: IncrementalRemoverEvent, is_local: bool = False) -> None:
    neptune_client = get_neptune_client(True)

    if event.remover_type == "works":
        ids = get_non_visible_work_ids(event, is_local)
    elif event.remover_type == "concepts":
        ids = get_unused_concept_ids()
    else:
        raise ValueError(f"Unknown remover type: '{event.remover_type}'")

    for batch in batched(ids, 10_000):
        print(f"Will delete a batch of {len(batch)} IDs from the catalogue graph.")
        neptune_client.delete_nodes_by_id(list(batch))

    # WORK EDGES not needed (detached automatically)
    # CONCEPT EDGES? (get all concept IDs connected to each work and compare? visible works only)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--remover-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which remover to run (works or concepts).",
        required=True,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline date associated with the removed items.",
        required=True,
    )

    args = parser.parse_args()
    event = IncrementalRemoverEvent(**args.__dict__)

    handler(event, is_local=True)


if __name__ == "__main__":
    local_handler()
