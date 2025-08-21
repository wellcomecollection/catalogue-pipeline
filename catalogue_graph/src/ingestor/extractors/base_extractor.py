from collections.abc import Generator
from typing import Any

from utils.aws import get_neptune_client


class GraphBaseExtractor:
    def __init__(self, start_offset: int, end_index: int, is_local: bool):
        self.neptune_client = get_neptune_client(is_local)
        self.start_offset = start_offset
        self.end_index = end_index

    def extract_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to items extracted from the catalogue graph."""
        raise NotImplementedError(
            "Each extractor must implement an `extract_raw` method."
        )

    def get_neptune_query_params(self) -> dict:
        return {
            "start_offset": self.start_offset,
            "limit": self.end_index - self.start_offset,
        }

    def make_neptune_query(self, query: str, label: str) -> list[dict]:
        params = self.get_neptune_query_params()
        return self.neptune_client.time_open_cypher_query(query, params, label)
