import time
from collections.abc import Generator
from queue import Queue
from threading import Thread

import config
from models.events import IncrementalWindow
from utils.elasticsearch import get_client, get_standard_index_name

from sources.base_source import BaseSource

ES_BATCH_SIZE = 1000


class MergedWorksSource(BaseSource):
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
        self.index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        self.query = self._build_query(query, window)
        self.fields = fields
        self.parallelism = parallelism

        pit = self.es_client.open_point_in_time(index=self.index_name, keep_alive="5m")
        self.pit_id = pit["id"]
    
    def _build_query(self, query: dict | None, window: IncrementalWindow | None) -> dict:
        full_query = {"match_all": {}} if query is None else query
        if window is not None:
            range_filter = window.to_merged_works_filter()
            full_query = {"bool": {"must": [full_query, range_filter]}}
        
        return full_query

    def search_with_pit(self, pit_id: str, slice_index: int, queue: Queue) -> None:
        body = {
            "query": self.query,
            "size": ES_BATCH_SIZE,
            "pit": {"id": pit_id, "keep_alive": "5m"},
            "sort": [{"_shard_doc": "asc"}],
        }  
        
        if self.parallelism > 1:
            body["slice"] = {"id": slice_index, "max": self.parallelism}

        if self.fields is not None:
            body["_source"] = self.fields

        while True:
            start_time = time.time()
            hits = self.es_client.search(body=body)["hits"]["hits"]
            duration = round(time.time() - start_time)

            if not hits:
                break

            print(
                f"Ran 'es works' query in {duration} seconds, retrieving {len(hits)} records."
            )

            for hit in hits:
                queue.put(hit.get("_source"))

            body["search_after"] = hits[-1]["sort"]

        queue.put(None)
    
    def _run_parallel_queries(self, queue: Queue):
        """Extract documents in parallel, with all threads adding resulting documents to the same queue"""
        for i in range(self.parallelism):
            # Run threads as daemons so that they automatically exit when the main thread throws an exception.
            # See https://docs.python.org/3/library/threading.html for more info.
            t = Thread(target=self.search_with_pit, args=(self.pit_id, i, queue), daemon=True)
            t.start()
    
    def stream_raw(self) -> Generator[dict]:
        queue: Queue = Queue(maxsize=ES_BATCH_SIZE)
        self._run_parallel_queries(queue)

        done_signals = 0
        while done_signals < self.parallelism:
            item = queue.get()
            if item is None:
                done_signals += 1
            else:
                yield item

        self.es_client.close_point_in_time(body={"id": self.pit_id})
