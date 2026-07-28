from __future__ import annotations

import gzip
import json
import os
import time
from collections.abc import Generator
from queue import Queue
from threading import Thread
from typing import Any

import backoff
import requests
import structlog
from elasticsearch import Elasticsearch
from elasticsearch.exceptions import ApiError, TransportError
from pydantic import BaseModel

import config

logger = structlog.get_logger(__name__)


class BaseSource:
    def stream_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to an entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")


class GZipSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[dict]:
        response = requests.get(self.url, stream=True)

        with gzip.GzipFile(fileobj=response.raw) as file:
            for line_bytes in file:
                yield json.loads(line_bytes.decode("utf8"))


class MultiGZipSource(BaseSource):
    def __init__(self, urls: list[str]):
        self.urls = urls

    def stream_raw(self) -> Generator[dict]:
        for url in self.urls:
            source = GZipSource(url)
            yield from source.stream_raw()


# The two 4xx statuses that clear on a retry; every other 4xx is a permanent
# problem (bad query, missing index, bad credentials) and fails fast.
RETRIABLE_4XX_STATUS_CODES = frozenset({408, 429})

# Time budget for retrying a single search: a blip must not fail the extractor and
# lose a window that has no backfill. Kept under the 15m PIT keep-alive.
ES_REQUESTS_BACKOFF_MAX_TIME = float(os.environ.get("REQUESTS_BACKOFF_MAX_TIME", "300"))
ES_REQUESTS_BACKOFF_MAX_INTERVAL = 30


class ErrorSentinel(BaseModel):
    exception: Any


def _giveup_es_request(exc: Exception) -> bool:
    if isinstance(exc, ApiError):
        status = exc.status_code
        return 400 <= status < 500 and status not in RETRIABLE_4XX_STATUS_CODES
    return False


def _on_request_backoff(backoff_details: Any) -> None:
    exc = backoff_details["exception"]
    logger.warning(
        "Elasticsearch request failed, retrying",
        exception_name=type(exc).__name__,
        status_code=getattr(exc, "status_code", None),
        elapsed_seconds=round(backoff_details["elapsed"]),
        tries=backoff_details["tries"],
    )


class ElasticSource(BaseSource):
    def __init__(
        self,
        es_client: Elasticsearch,
        index_name: str,
        query: dict,
        pit_id: str | None = None,
        fields: list | None = None,
        batch_size: int = config.ES_SOURCE_BATCH_SIZE,
        slice_count: int = config.ES_SOURCE_SLICE_COUNT,
        parallelism: int = config.ES_SOURCE_PARALLELISM,
    ):
        self.es_client = es_client
        self.index_name = index_name
        self.query = query
        self.fields = fields
        self.batch_size = batch_size
        self.slice_count = slice_count
        self.parallelism = parallelism

        if pit_id is not None:
            self.pit_id = pit_id
        else:
            pit = self.es_client.open_point_in_time(
                index=self.index_name, keep_alive="15m"
            )
            self.pit_id = pit["id"]

    @backoff.on_exception(
        backoff.expo,
        (TransportError, ApiError),
        max_time=ES_REQUESTS_BACKOFF_MAX_TIME,
        max_value=ES_REQUESTS_BACKOFF_MAX_INTERVAL,
        giveup=_giveup_es_request,
        on_backoff=_on_request_backoff,
        jitter=backoff.full_jitter,
    )
    def search(self, slice_index: int, search_after: str | None = None) -> list[dict]:
        body: dict[str, Any] = {
            "query": self.query,
            "size": self.batch_size,
            "pit": {"id": self.pit_id, "keep_alive": "15m"},
            "sort": [{"_shard_doc": "asc"}],
        }

        if self.slice_count > 1:
            body["slice"] = {"id": slice_index, "max": self.slice_count}
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
            try:
                self.worker_target(slice_index, queue)
            except Exception as e:
                queue.put(ErrorSentinel(exception=e))

        t = Thread(target=worker, daemon=True)
        t.start()

    def stream_raw(self) -> Generator[Any]:
        queue: Queue = Queue(maxsize=self.batch_size)

        next_thread_index = 0
        for i in range(min(self.slice_count, self.parallelism)):
            self.run_worker(i, queue)
            next_thread_index += 1

        done_signals = 0
        while done_signals < self.slice_count:
            item = queue.get()
            if item is None:
                done_signals += 1

                if next_thread_index < self.slice_count:
                    self.run_worker(next_thread_index, queue)
                    next_thread_index += 1
            elif isinstance(item, ErrorSentinel):
                raise item.exception
            else:
                yield item
