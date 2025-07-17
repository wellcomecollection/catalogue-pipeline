from collections.abc import Generator
from queue import Queue
from threading import Thread

import config
from utils.elasticsearch import get_client

from .base_source import BaseSource

ES_BATCH_SIZE = 1000


class ElasticsearchSource(BaseSource):
    def __init__(
        self,
        pipeline_date: str | None,
        is_local: bool,
        index_name: str,
        query: dict | None = None,
        fields: list | None = None,
    ):
        self.es_client = get_client("work_ingestor", pipeline_date, is_local)
        self.index_name = index_name
        self.query = {"match_all": {}} if query is None else query
        self.fields = fields

    def search_with_pit(self, pit_id: int, slice_index: int, queue: Queue) -> None:
        body = {
            "query": self.query,
            "size": ES_BATCH_SIZE,
            "pit": {"id": pit_id, "keep_alive": "5m"},
            "sort": [{"_shard_doc": "asc"}],
            "slice": {"id": slice_index, "max": config.ES_SOURCE_PARALLELISM},
        }

        if self.fields is not None:
            body["_source"] = self.fields

        while True:
            hits = self.es_client.search(body=body)["hits"]["hits"]
            if not hits:
                break

            for hit in hits:
                queue.put(hit.get("_source"))

            body["search_after"] = hits[-1]["sort"]

        queue.put(None)

    def stream_raw(self) -> Generator[dict]:
        pit = self.es_client.open_point_in_time(index=self.index_name, keep_alive="5m")

        q: Queue = Queue(maxsize=ES_BATCH_SIZE)
        threads = []
        for i in range(config.ES_SOURCE_PARALLELISM):
            t = Thread(target=self.search_with_pit, args=(pit["id"], i, q))
            t.start()
            threads.append(t)

        done_signals = 0
        while done_signals < config.ES_SOURCE_PARALLELISM:
            item = q.get()
            if item is None:
                done_signals += 1
            else:
                yield item

        self.es_client.close_point_in_time(body={"id": pit["id"]})
