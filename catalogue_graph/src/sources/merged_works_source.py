import time
from collections.abc import Generator
from datetime import timedelta
from queue import Queue
from threading import Thread
from typing import Any

import config
from models.events import IncrementalWindow
from utils.elasticsearch import get_client, get_standard_index_name

from sources.base_source import BaseSource

ES_BATCH_SIZE = 2000
NUM_SLICES = 30


def build_merged_index_query(
    query: dict | None, window: IncrementalWindow | None, i: int
) -> dict:
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


class MergedWorksSource(BaseSource):
    def __init__(
        self,
        pipeline_date: str,
        query: dict | None = None,
        fields: list | None = None,
        window: IncrementalWindow | None = None,
        is_local: bool = False,
        parallelism: int = config.ES_SOURCE_PARALLELISM,
    ):
        self.es_client = get_client("graph_extractor", pipeline_date, is_local)
        self.parallelism = parallelism
        self.window = window
        self.fields = fields
        self.query = query
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        pit = self.es_client.open_point_in_time(index=index_name, keep_alive="15m")
        self.pit_id = pit["id"]

    def search(self, slice_index: int, search_after: str | None = None) -> list[dict]:
        query = build_merged_index_query(self.query, self.window, slice_index)
        body = {
            "query": query,
            "size": ES_BATCH_SIZE,
            "pit": {"id": self.pit_id, "keep_alive": "15m"},
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
        search_after = None
        while hits := self.search(slice_index, search_after):
            for hit in hits:
                queue.put(hit["_source"])

            search_after = hits[-1]["sort"]

        queue.put(None)

    def run_worker(self, slice_index: int, queue: Queue):
        # Run threads as daemons so that they automatically exit when the main thread throws an exception.
        # See https://docs.python.org/3/library/threading.html for more info.
        t = Thread(target=self.worker_target, args=(slice_index, queue), daemon=True)
        t.start()

    def stream_raw(self) -> Generator[Any]:
        queue: Queue = Queue(maxsize=ES_BATCH_SIZE)

        next_thread_index = 0
        # Extract documents in parallel, with all threads adding resulting documents to the same queue
        for i in range(self.parallelism):
            self.run_worker(i, queue)
            next_thread_index += 1

        done_signals = 0
        while done_signals < NUM_SLICES:
            item = queue.get()
            if item is None:
                done_signals += 1

                if next_thread_index < NUM_SLICES:
                    self.run_worker(next_thread_index, queue)
                    next_thread_index += 1
            else:
                yield item

        self.es_client.close_point_in_time(body={"id": self.pit_id})
