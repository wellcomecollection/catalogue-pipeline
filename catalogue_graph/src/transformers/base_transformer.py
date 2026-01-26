import concurrent.futures
import csv
import os
from collections.abc import Generator
from itertools import batched, islice
from typing import Any, TextIO

import boto3
import smart_open
import structlog

from clients.base_neptune_client import BaseNeptuneClient
from converters.cypher.bulk_load_converter import CypherBulkLoadConverter
from models.events import EntityType
from models.graph_edge import BaseEdge
from models.graph_node import BaseNode
from query_builders.cypher import construct_upsert_cypher_query
from sources.base_source import BaseSource
from utils.aws import publish_batch_to_sns

logger = structlog.get_logger(__name__)
CHUNK_SIZE = int(os.environ.get("TRANSFORMER_CHUNK_SIZE", "256"))


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()

    def transform_node(self, raw_node: Any) -> BaseNode | None:
        """Accepts a raw node from the source dataset and returns a transformed node as a Pydantic model."""
        raise NotImplementedError(
            "Each transformer must implement a `transform_node` method."
        )

    def extract_edges(self, raw_node: Any) -> Generator[BaseEdge]:
        """Accepts a raw node from the source dataset and returns a generator of extracted edges as Pydantic models."""
        raise NotImplementedError(
            "Each transformer must implement an `extract_edges` method."
        )

    def _stream_nodes(self, number: int | None = None) -> Generator[BaseNode]:
        """
        Extracts nodes from the specified source and transforms them. The `source` must define a `stream_raw` method.
        Takes an optional parameter to only extract the first `number` nodes.
        """
        counter = 0

        for raw_node in self.source.stream_raw():
            node = self.transform_node(raw_node)

            if node:
                yield node
                counter += 1

                if counter % 10000 == 0:
                    logger.info("Streaming nodes progress", count=counter)
            if counter == number:
                return

        logger.info("Streamed all nodes", count=counter)

    def _stream_edges(self, number: int | None = None) -> Generator[BaseEdge]:
        """
        Extracts edges from the specified source and transforms them. The `source` must define a `stream_raw` method.
        Takes an optional parameter to only extract the first `number` edges.
        """
        counter = 0

        for raw_node in self.source.stream_raw():
            edges = self.extract_edges(raw_node)

            for edge in edges:
                yield edge

                counter += 1
                if counter % 10000 == 0:
                    logger.info("Streaming edges progress", count=counter)
                if counter == number:
                    return

        logger.info("Streamed all edges", count=counter)

    def _stream_entities(
        self, entity_type: EntityType, sample_size: int | None = None
    ) -> Generator[BaseNode | BaseEdge]:
        entities: Generator[BaseNode | BaseEdge]  # Make mypy happy

        if entity_type == "nodes":
            entities = self._stream_nodes(sample_size)
        elif entity_type == "edges":
            entities = self._stream_edges(sample_size)
        else:
            raise ValueError("Unsupported entity type.")

        yield from entities

    def _stream_to_bulk_load_file(
        self, file: TextIO, entity_type: EntityType, sample_size: int | None = None
    ) -> None:
        """Streams entities to a file in the openCypher format for Neptune bulk load."""
        csv_writer = None
        converter = CypherBulkLoadConverter(entity_type)

        for chunk in self._stream_chunks(entity_type, sample_size):
            bulk_dicts = []
            for entity in chunk:
                bulk_dict = converter.convert_to_bulk_cypher(entity)
                bulk_dicts.append(bulk_dict)

            if csv_writer is None:
                csv_writer = csv.DictWriter(file, fieldnames=bulk_dicts[0].keys())
                csv_writer.writeheader()

            csv_writer.writerows(bulk_dicts)

    def _stream_chunks(
        self, entity_type: EntityType, sample_size: int | None = None
    ) -> Generator[list[BaseNode | BaseEdge]]:
        """
        Extracts the specified entity type (nodes or edges) from its source, transforms each entity,
        and returns the results stream in fixed-size chunks.
        """
        entities = self._stream_entities(entity_type, sample_size)
        yield from batched(entities, CHUNK_SIZE)

    def stream_to_s3(
        self, s3_uri: str, entity_type: EntityType, sample_size: int | None = None
    ) -> None:
        """
        Streams transformed entities (nodes or edges) into an S3 bucket for bulk loading into the Neptune cluster.
        Suitable for indexing large numbers of entities in production. Provides limited observability.
        """
        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_uri, "w", transport_params=transport_params) as f:
            self._stream_to_bulk_load_file(f, entity_type, sample_size)

    def stream_to_graph(
        self,
        neptune_client: BaseNeptuneClient,
        entity_type: EntityType,
        sample_size: int | None = None,
    ) -> None:
        """
        Streams transformed entities (nodes or edges) directly into Neptune using multiple threads for parallel
        processing. Suitable for local testing. Not recommended for indexing large numbers of entities.
        """
        chunks = self._stream_chunks(entity_type, sample_size)

        def run_query(chunk: list[BaseNode | BaseEdge]) -> None:
            query = construct_upsert_cypher_query(chunk, entity_type)
            neptune_client.run_open_cypher_query(query)

        with concurrent.futures.ThreadPoolExecutor() as executor:
            # Run the first 10 queries in parallel
            futures = {
                executor.submit(run_query, chunk)
                for i, chunk in enumerate(islice(chunks, 10))
            }

            while futures:
                # Wait for one or more queries to complete
                done, futures = concurrent.futures.wait(
                    futures, return_when=concurrent.futures.FIRST_COMPLETED
                )

                for future in done:
                    future.result()

                # Top up with new queries to keep the total number of parallel queries at 10
                for chunk in islice(chunks, len(done)):
                    futures.add(executor.submit(run_query, chunk))

    def stream_to_sns(
        self, topic_arn: str, entity_type: EntityType, sample_size: int | None = None
    ) -> None:
        """
        Streams transformed entities (nodes or edges) into an SNS topic as openCypher queries, where they will be
        consumed by the `indexer` Lambda function.
        """
        queries = []

        for i, chunk in enumerate(self._stream_chunks(entity_type, sample_size)):
            queries.append(construct_upsert_cypher_query(chunk, entity_type))

            # SNS supports a maximum batch size of 10
            if len(queries) >= 10:
                publish_batch_to_sns(topic_arn, queries)
                queries = []

            if (i + 1) % 100 == 0:
                logger.info("Published messages to SNS", count=i + 1)

        # Publish remaining messages (if any)
        if len(queries) > 0:
            publish_batch_to_sns(topic_arn, queries)

    def stream(
        self, entity_type: EntityType, sample_size: int | None = None
    ) -> Generator:
        """
        Streams transformed entities (nodes or edges) as a generator. Useful for development and testing purposes.
        """
        yield from self._stream_chunks(entity_type, sample_size)

    def stream_to_local_file(
        self, file_name: str, entity_type: EntityType, sample_size: int | None = None
    ) -> str:
        """
        Streams transformed entities (nodes or edges) into the local `transformer_outputs` folder.
        Useful for development and testing purposes.
        """
        file_path = f"../transformer_outputs/{file_name}"
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        with open(file_path, "w") as f:
            self._stream_to_bulk_load_file(f, entity_type, sample_size)

        return os.path.abspath(file_path)
