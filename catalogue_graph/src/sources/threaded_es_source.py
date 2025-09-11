import time
from collections.abc import Generator
from queue import Queue
from threading import Thread
from typing import Any

import config
from models.events import IncrementalWindow
from utils.elasticsearch import get_client, get_standard_index_name

from sources.base_source import BaseSource

ES_BATCH_SIZE = 1000


def build_merged_index_query(query: dict | None, window: IncrementalWindow | None) -> dict:
    full_query = {"match_all": {}} if query is None else query
    if window is not None:
        range_filter = window.to_merged_works_filter()
        full_query = {"bool": {"must": [full_query, range_filter]}}

    return full_query


class ThreadedElasticsearchSource(BaseSource):
    def __init__(
            self,
            pipeline_date: str,
            query: dict | None = None,
            fields: list | None = None,
            window: IncrementalWindow | None = None,
            is_local: bool = False,
            parallelism: int = config.ES_SOURCE_PARALLELISM
    ):
        self.es_client = get_client("graph_extractor", pipeline_date, is_local)
        self.parallelism = parallelism
        self.query = build_merged_index_query(query, window)
        self.fields = fields

        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        pit = self.es_client.open_point_in_time(index=index_name, keep_alive="5m")
        self.pit_id = pit["id"]

    def search(self, slice_index: int, search_after: str | None = None) -> list[dict]:
        body = {
            "query": self.query,
            "size": ES_BATCH_SIZE,
            "pit": {"id": self.pit_id, "keep_alive": "5m"},
            "sort": [{"_shard_doc": "asc"}],
        }

        if self.parallelism > 1:
            body["slice"] = {"id": slice_index, "max": self.parallelism}
        if self.fields is not None:
            body["_source"] = self.fields
        if search_after is not None:
            body["search_after"] = search_after
        
        start_time = time.time()
        hits = self.es_client.search(body=body)["hits"]["hits"]
        duration = round(time.time() - start_time)

        print(
            f"Ran Elasticsearch query (slice {slice_index}) in {duration} seconds, retrieving {len(hits)} records."
        )
            
        return hits
    
    def worker_target(self, slice_index: int, queue: Queue) -> None:
        raise NotImplementedError()

    def stream_raw(self) -> Generator[Any]:
        queue: Queue = Queue(maxsize=ES_BATCH_SIZE)

        # Extract documents in parallel, with all threads adding resulting documents to the same queue
        for i in range(self.parallelism):
            # Run threads as daemons so that they automatically exit when the main thread throws an exception.
            # See https://docs.python.org/3/library/threading.html for more info.
            t = Thread(target=self.worker_target, args=(i, queue), daemon=True)
            t.start()

        done_signals = 0
        while done_signals < self.parallelism:
            item = queue.get()
            if item is None:
                done_signals += 1
            else:
                yield item

        self.es_client.close_point_in_time(body={"id": self.pit_id})
