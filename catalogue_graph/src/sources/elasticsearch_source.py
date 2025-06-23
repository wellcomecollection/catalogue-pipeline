from collections.abc import Generator
from typing import Tuple

from elasticsearch import Elasticsearch

from .base_source import BaseSource

ES_BATCH_SIZE = 10000


class ElasticsearchSource(BaseSource):
    def __init__(self, url: str, index_name: str, basic_auth: Tuple[str, str]):
        self.es_client = Elasticsearch(url, basic_auth=basic_auth, timeout=120)
        self.index_name = index_name

    def search_with_pit(self, pit_id: int):
        body = {
            "query": {"match": {"type": "Visible"}},
            "size": ES_BATCH_SIZE,
            "pit": {"id": pit_id, "keep_alive": "2m"},
            "sort": [{"_shard_doc": "asc"}],
        }

        while True:
            hits = self.es_client.search(body=body)["hits"]["hits"]
            if not hits:
                break

            yield from hits

            body["search_after"] = hits[-1]["sort"]

    def stream_raw(self) -> Generator[dict]:
        pit = self.es_client.open_point_in_time(index=self.index_name, keep_alive="2m")

        yield from self.search_with_pit(pit["id"])

        self.es_client.close_point_in_time(body={"id": pit["id"]})
