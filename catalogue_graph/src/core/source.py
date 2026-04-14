from __future__ import annotations

import gzip
import json
import os
import time
from collections.abc import Generator
from queue import Queue
from threading import Thread
from typing import Any, Self

import backoff
import requests
import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

import config
from models.source_document_selection import SourceDocumentSelection

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


ES_REQUESTS_BACKOFF_RETRIES = int(os.environ.get("REQUESTS_BACKOFF_RETRIES", "3"))
ES_REQUESTS_BACKOFF_INTERVAL = 10


class ErrorSentinel(BaseModel):
    exception: Any


def _on_request_backoff(backoff_details: Any) -> None:
    exception_name = type(backoff_details["exception"]).__name__
    logger.warning(
        "Elasticsearch request failed, retrying", exception_name=exception_name
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
        backoff.constant,
        Exception,
        max_tries=ES_REQUESTS_BACKOFF_RETRIES,
        interval=ES_REQUESTS_BACKOFF_INTERVAL,
        on_backoff=_on_request_backoff,
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

    @classmethod
    def from_document_selection(
        cls,
        document_selection: SourceDocumentSelection,
        range_filter_field_name: str,
        query: dict | None = None,
        **kwargs: Any,
    ) -> Self:
        query = {"match_all": {}} if query is None else query
        range_or_id_filter = document_selection.to_elasticsearch_query(
            range_filter_field_name
        )
        full_query = {"bool": {"must": [query, range_or_id_filter]}}

        return cls(
            query=full_query,
            **kwargs,
        )
