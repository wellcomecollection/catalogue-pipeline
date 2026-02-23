import os
import time
from collections.abc import Generator
from queue import Queue
from threading import Thread
from typing import Any

import backoff
import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

import config
from models.events import BasePipelineEvent, IncrementalWindow
from sources.base_source import BaseSource
from utils.elasticsearch import get_merged_index_name

logger = structlog.get_logger(__name__)

ES_MGET_BATCH_SIZE = 10_000
ES_REQUESTS_BACKOFF_RETRIES = int(os.environ.get("REQUESTS_BACKOFF_RETRIES", "3"))
ES_REQUESTS_BACKOFF_INTERVAL = 10


class ErrorSentinel(BaseModel):
    exception: Any


def on_request_backoff(backoff_details: Any) -> None:
    exception_name = type(backoff_details["exception"]).__name__
    logger.warning(
        "Elasticsearch request failed, retrying", exception_name=exception_name
    )


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
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        query: dict | None = None,
        fields: list | None = None,
    ):
        self.es_client = es_client
        self.window = event.window
        self.fields = fields
        self.query = query

        self.index_name = get_merged_index_name(event)

        # Use the provided point in time (PIT) ID, or create a new one
        if event.pit_id is not None:
            self.pit_id = event.pit_id
        else:
            pit = self.es_client.open_point_in_time(
                index=self.index_name, keep_alive="15m"
            )
            self.pit_id = pit["id"]

    @backoff.on_exception(
        backoff.constant,
        Exception,
        max_tries=ES_REQUESTS_BACKOFF_RETRIES,
        interval=ES_REQUESTS_BACKOFF_INTERVAL,
        on_backoff=on_request_backoff,
    )
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
        result = self.es_client.search(body=body)
        hits: list[dict] = result["hits"]["hits"]
        duration = round(time.time() - start_time)

        if result.get("pit_id"):
            self.pit_id = result["pit_id"]

        logger.info(
            "Ran Elasticsearch query",
            slice_index=slice_index,
            duration_seconds=duration,
            record_count=len(hits),
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
        def worker() -> None:
            # Propagate all exceptions into the main thread
            try:
                self.worker_target(slice_index, queue)
            except Exception as e:
                queue.put(ErrorSentinel(exception=e))

        # Run threads as daemons so that they automatically exit when the main thread throws an exception.
        # See https://docs.python.org/3/library/threading.html for more info.
        t = Thread(target=worker, daemon=True)
        t.start()

    def stream_raw(self) -> Generator[Any]:
        queue: Queue = Queue(maxsize=config.ES_SOURCE_BATCH_SIZE)

        next_thread_index = 0
        # Extract documents in parallel, with all threads adding resulting documents to the same queue
        for i in range(min(config.ES_SOURCE_SLICE_COUNT, config.ES_SOURCE_PARALLELISM)):
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
            elif isinstance(item, ErrorSentinel):
                raise item.exception
            else:
                yield item
