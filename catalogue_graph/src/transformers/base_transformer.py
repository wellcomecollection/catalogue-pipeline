import csv
import os
from collections.abc import Generator
from itertools import batched
from typing import Any, TextIO

import boto3
import smart_open
import structlog
from converters.cypher.bulk_load_converter import CypherBulkLoadConverter
from models.events import EntityType
from models.graph_edge import BaseEdge
from models.graph_node import BaseNode
from sources.base_source import BaseSource

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
