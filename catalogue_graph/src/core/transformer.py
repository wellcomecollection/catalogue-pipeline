from collections.abc import Generator, Iterable
from itertools import batched
from typing import Any, TypeVar, cast

import elasticsearch.helpers
import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from core.source import BaseSource

T = TypeVar("T", bound=BaseModel)

logger = structlog.get_logger(__name__)

TRANSFORM_BATCH_SIZE = 10_000
ES_BULK_INDEX_BATCH_SIZE = 10_000


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()


class TransformationError(BaseModel):
    row_id: str
    stage: str
    detail: str


class ElasticBaseTransformer[T: BaseModel](BaseTransformer):
    def __init__(self) -> None:
        super().__init__()
        self.successful_ids: list[str] = []
        self.errors: list[TransformationError] = []
        self.error_ids: set[str] = set()

        self.source_id_to_row_id: dict[str, str] = {}

    def _add_error(self, exception: Exception | dict, stage: str, row_id: str) -> None:
        error = TransformationError(
            stage=stage, row_id=row_id, detail=str(exception)[:500]
        )
        # Only keep track of the first 1000 errors to cap manifest file sizes
        if len(self.errors) < 1_000 and row_id not in self.error_ids:
            self.error_ids.add(row_id)
            self.errors.append(error)

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[tuple[str, T]]:
        """Transform a batch of raw items into (row_id, T) tuples."""
        raise NotImplementedError(
            "Each transformer must implement a `transform` method."
        )

    def _get_document_id(self, record: T) -> str:
        """Extract the document ID for Elasticsearch indexing."""
        raise NotImplementedError(
            "Each transformer must implement a `_get_document_id` method."
        )

    def _transform_batches(self) -> Generator[tuple[list[Any], list[T]]]:
        """
        Extracts documents from the specified source and transforms them. The `source` must define
        a `stream_raw` method.
        """
        raw_works = self.source.stream_raw()
        for raw_batch in batched(raw_works, TRANSFORM_BATCH_SIZE):
            transformed_batch = []
            for row_id, record in self.transform(raw_batch):
                source_id = self._get_document_id(record)
                self.source_id_to_row_id[source_id] = row_id
                transformed_batch.append(record)

            logger.info(
                "Transformed batch",
                transformed_count=len(transformed_batch),
                batch_size=len(raw_batch),
            )

            yield list(raw_batch), transformed_batch

    def _generate_bulk_load_actions(
        self, records: Iterable[T], index_name: str
    ) -> Generator[dict[str, Any]]:
        for record in records:
            yield {
                "_index": index_name,
                "_id": self._get_document_id(record),
                "_source": record.model_dump(),
            }

    def _index_es_batch(
        self, es_client: Elasticsearch, es_actions: list[dict]
    ) -> list[dict[str, Any]]:
        success_count, es_errors = elasticsearch.helpers.bulk(
            es_client,
            es_actions,
            raise_on_error=False,
            stats_only=False,
        )
        logger.info(
            "Indexed batch",
            success_count=success_count,
            batch_size=len(es_actions),
        )

        # Since we called `bulk` with `stats_only=False`, we know that es_errors is a list of dicts
        return cast(list[dict[str, Any]], es_errors)

    def stream_to_index(self, es_client: Elasticsearch, index_name: str) -> None:
        # Reset run-specific state so manifests reflect the current execution only
        self.successful_ids.clear()
        self.errors.clear()

        for raw_batch, transformed_batch in self._transform_batches():
            es_actions = list(
                self._generate_bulk_load_actions(transformed_batch, index_name)
            )
            es_errors = self._index_es_batch(es_client, es_actions)

            batch_error_ids = set()
            for e in es_errors:
                source_id = e["index"]["_id"]
                batch_error_ids.add(source_id)

                row_id = self.source_id_to_row_id[source_id]
                logger.warning(
                    "Indexing error",
                    row_id=row_id,
                    source_id=source_id,
                    error=e,
                )
                self._add_error(e, "index", row_id)

            batch_ids = [a["_id"] for a in es_actions]
            batch_success_ids = [i for i in batch_ids if i not in batch_error_ids]
            self.successful_ids.extend(batch_success_ids)
            self._commit(
                raw_batch,
                {self.source_id_to_row_id[i] for i in batch_success_ids},
                {self.source_id_to_row_id[i] for i in batch_error_ids},
            )

    def _commit(
        self,
        raw_batch: Iterable[dict],
        success_row_ids: set[str],
        error_row_ids: set[str],
    ) -> None:
        """Optional post-index commit hook called once per source batch."""
