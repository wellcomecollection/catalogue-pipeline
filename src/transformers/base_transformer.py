from collections.abc import Generator
from models.graph_edge import BaseEdge
from models.graph_node import BaseNode
from sources.base_source import BaseSource

from typing import Literal
from itertools import islice


def _generator_to_chunks(items: Generator, chunk_size: int) -> Generator:
    while True:
        chunk = list(islice(items, chunk_size))
        if chunk:
            yield chunk
        else:
            return


class BaseTransformer:
    def __init__(self):
        self.source: BaseSource = BaseSource()

    def transform_node(self, raw_node: dict) -> BaseNode | None:
        raise NotImplementedError(
            "Each transformer must implement a `transform_node` method."
        )

    def extract_edges(self, raw_node: dict) -> Generator[BaseEdge]:
        raise NotImplementedError(
            "Each transformer must implement an `extract_edges` method."
        )

    def stream_nodes(self, number: int = None) -> Generator[BaseNode]:
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
            if counter == number:
                return

    def stream_edges(self, number: int = None) -> Generator[BaseEdge]:
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
                if counter == number:
                    return

    def stream_chunks(
        self,
        entity_type: Literal["nodes", "edges"],
        chunk_size: int,
        sample_size: int = None,
    ) -> Generator[list[BaseNode | BaseEdge]]:
        """
        Extracts the specified entity type (nodes or edges) from its source, transforms them,
        and returns the results stream in fixed-size chunks.
        """
        if entity_type == "nodes":
            entities = self.stream_nodes(sample_size)
        elif entity_type == "edges":
            entities = self.stream_edges(sample_size)
        else:
            raise ValueError("Unsupported entity type.")

        for chunk in _generator_to_chunks(entities, chunk_size):
            yield chunk
