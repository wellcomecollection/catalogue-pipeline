import time
from collections.abc import Generator
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import timedelta
from queue import Queue
from typing import Any

import config
from models.events import IncrementalWindow
from utils.elasticsearch import get_client, get_standard_index_name

from sources.base_source import BaseSource

ES_BATCH_SIZE = 2000
NUM_SLICES = 15


def build_merged_index_query(query: dict | None, window: IncrementalWindow | None, i: int) -> dict:
    full_query = {"match_all": {}} if query is None else query
    if window is not None:
        range_filter = split_time_window(window, i)
        full_query = {"bool": {"must": [full_query, range_filter]}}

    return full_query


def split_time_window(window, i: int):
    total_seconds = (window.end_time - window.start_time).total_seconds()
    step = total_seconds / NUM_SLICES

    start_time = window.start_time + timedelta(seconds=i * step)
    end_time = window.start_time + timedelta(seconds=(i + 1) * step)

    return {
        "range": {
            "state.mergedTime": {
                "gte": start_time.isoformat(),
                "lte": end_time.isoformat(),
            }
        }
    }

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
        self.window = window
        self.fields = fields
        self.query = query
        
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        pit = self.es_client.open_point_in_time(index=index_name, keep_alive="5m")
        self.pit_id = pit["id"]

    def search(self, slice_index: int, search_after: str | None = None) -> list[dict]:
        query = build_merged_index_query(self.query, self.window, slice_index)
        body = {
            "query": query,
            "size": ES_BATCH_SIZE,
            "pit": {"id": self.pit_id, "keep_alive": "5m"},
            "sort": [{"_shard_doc": "asc"}],
        }

        # if NUM_SLICES > 1:
        #     body["slice"] = {"id": slice_index, "max": NUM_SLICES}
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
        
        done_signals = 0
        with ThreadPoolExecutor(max_workers=self.parallelism) as executor:
            futures = [executor.submit(self.worker_target, i, queue) for i in range(NUM_SLICES)]
    
            # Consume results from queue while workers are running
            while done_signals < NUM_SLICES:
                item = queue.get()
                if item is None:
                    done_signals += 1
                else:
                    yield item
    
            # Ensure all futures finish / propagate errors
            for f in as_completed(futures):
                f.result()

        self.es_client.close_point_in_time(body={"id": self.pit_id})
