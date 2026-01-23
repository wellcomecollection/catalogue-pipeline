from collections.abc import Generator, Iterable
from itertools import batched
from typing import Any, cast

import elasticsearch.helpers
import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from models.pipeline.source.work import SourceWork

logger = structlog.get_logger(__name__)

ES_BULK_INDEX_BATCH_SIZE = 10_000


class TransformationError(BaseModel):
    row_id: str
    stage: str
    detail: str


class BaseSource:
    def stream_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to an entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()
        self.successful_ids: list[str] = []
        self.errors: list[TransformationError] = []

    def _add_error(self, exception: Exception | dict, stage: str, row_id: str) -> None:
        error = TransformationError(
            stage=stage, row_id=row_id, detail=str(exception)[:500]
        )
        # Only keep track of the first 1000 errors to cap manifest file sizes
        if len(self.errors) < 1_000:
            self.errors.append(error)

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[tuple[str, SourceWork]]:
        """Transform a batch of raw works into (row_id, SourceWork) tuples."""
        raise NotImplementedError(
            "Each transformer must implement a `transform` method."
        )

    def _stream_works(self) -> Generator[tuple[str, SourceWork]]:
        """
        Extracts work documents from the specified source and transforms them. The `source` must define
        a `stream_raw` method.
        """
        raw_works = self.source.stream_raw()
        for batch in batched(raw_works, 10_000):
            transformed = list(self.transform(batch))
            logger.info(
                "Transformed batch",
                transformed_count=len(transformed),
                batch_size=len(batch),
            )

            yield from transformed

    def _generate_bulk_load_actions(
        self, records: Iterable[tuple[str, SourceWork]], index_name: str
    ) -> Generator[tuple[str, dict[str, Any]]]:
        for row_id, record in records:
            action = {
                "_index": index_name,
                "_id": record.state.id(),
                "_source": record.model_dump(),
            }
            yield (row_id, action)

    def stream_to_index(self, es_client: Elasticsearch, index_name: str) -> None:
        # Reset run-specific state so manifests reflect the current execution only
        self.successful_ids.clear()
        self.errors.clear()

        transformed = self._stream_works()
        actions = self._generate_bulk_load_actions(transformed, index_name)

        for batch in batched(actions, ES_BULK_INDEX_BATCH_SIZE):
            # Split row_ids from actions for ES bulk helper
            batch_list = list(batch)
            row_ids_by_source_id = {
                action["_id"]: row_id for row_id, action in batch_list
            }
            es_actions = [action for _, action in batch_list]

            success_count, es_errors = elasticsearch.helpers.bulk(
                es_client,
                es_actions,
                raise_on_error=False,
                stats_only=False,
            )

            # Since we called `bulk` with `stats_only=False`, we know that es_errors is a list of dicts
            es_errors = cast(list[dict[str, Any]], es_errors)

            logger.info(
                "Indexed batch",
                success_count=success_count,
                batch_size=len(es_actions),
            )

            error_ids = set()
            for e in es_errors:
                source_id = e["index"]["_id"]
                error_ids.add(source_id)

                row_id = row_ids_by_source_id[source_id]
                logger.warning(
                    "Indexing error",
                    row_id=row_id,
                    source_id=source_id,
                    error=e,
                )
                self._add_error(e, "index", row_id)

                if source_id not in row_ids_by_source_id:
                    raise KeyError(f"No row_id found for source_id={source_id}!")

            for source_id in row_ids_by_source_id:
                if source_id not in error_ids:
                    self.successful_ids.append(source_id)
