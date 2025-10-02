import time
from collections.abc import Generator
from queue import Queue
from threading import Thread
from typing import Any

import config
from models.events import IncrementalWindow
from sources.base_source import BaseSource
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name


def build_merged_index_query(
    query: dict | None,
    window: IncrementalWindow | None,
) -> dict:
    full_query = {"match_all": {}} if query is None else query
    if window is not None:
        range_filter = {
            "range": {
                "state.mergedTime": {
                    "gte": window.start_time.isoformat(),
                    "lte": window.end_time.isoformat(),
                }
            }
        }
        full_query = {"bool": {"must": [full_query, range_filter]}}

    return full_query


class MergedWorksSource(BaseSource):
    def __init__(
        self,
        pipeline_date: str,
        query: dict | None = None,
        fields: list | None = None,
        window: IncrementalWindow | None = None,
        es_mode: ElasticsearchMode = "private",
    ):
        self.es_client = get_client("graph_extractor", pipeline_date, es_mode)
        self.window = window
        self.fields = fields
        self.query = query
        self.index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        pit = self.es_client.open_point_in_time(index=self.index_name, keep_alive="15m")
        self.pit_id = pit["id"]

    def mget(self, work_ids: list[str]) -> Generator[dict]:
        """Retrieve work documents by ID"""
        start_time = time.time()
        result = self.es_client.mget(index=self.index_name, body={"ids": work_ids})
        duration = round(time.time() - start_time)

        print(
            f"Ran Elasticsearch query in {duration} seconds, retrieving {len(result['docs'])} records."
        )

        for work in result["docs"]:
            if "error" in work:
                raise ValueError(
                    f"Failed to retrieve work from Elasticsearch: {work['error']}"
                )
            if not work["found"]:
                print(f"Work {work['_id']} does not exist in the denormalised index.")
            else:
                yield work["_source"]

    def search(self, slice_index: int, search_after: str | None = None) -> list[dict]:
        query = build_merged_index_query(self.query, self.window)
        body = {
            "query": query,
            "size": config.ES_SOURCE_BATCH_SIZE,
            "pit": {"id": self.pit_id, "keep_alive": "15m"},
            "sort": [{"_shard_doc": "asc"}],
        }

        if config.ES_SOURCE_SLICE_COUNT > 1:
            body["slice"] = {"id": slice_index, "max": config.ES_SOURCE_SLICE_COUNT}
        if self.fields is not None:
            body["_source"] = self.fields
        if search_after is not None:
            body["search_after"] = search_after

        start_time = time.time()
        hits: list[dict] = self.es_client.search(body=body)["hits"]["hits"]
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

    def run_worker(self, slice_index: int, queue: Queue) -> None:
        # Run threads as daemons so that they automatically exit when the main thread throws an exception.
        # See https://docs.python.org/3/library/threading.html for more info.
        t = Thread(target=self.worker_target, args=(slice_index, queue), daemon=True)
        t.start()

    def stream_raw(self) -> Generator[Any]:
        queue: Queue = Queue(maxsize=config.ES_SOURCE_BATCH_SIZE)

        next_thread_index = 0
        # Extract documents in parallel, with all threads adding resulting documents to the same queue
        for i in range(config.ES_SOURCE_PARALLELISM):
            self.run_worker(i, queue)
            next_thread_index += 1

        done_signals = 0
        while done_signals < config.ES_SOURCE_SLICE_COUNT:
            item = queue.get()
            if item is None:
                done_signals += 1

                if next_thread_index < config.ES_SOURCE_SLICE_COUNT:
                    self.run_worker(next_thread_index, queue)
                    next_thread_index += 1
            else:
                yield item

        self.es_client.close_point_in_time(body={"id": self.pit_id})
