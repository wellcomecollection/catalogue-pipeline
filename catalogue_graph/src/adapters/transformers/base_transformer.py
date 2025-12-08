from collections.abc import Generator, Iterable
from itertools import batched
from typing import Any, cast

import elasticsearch.helpers
from elasticsearch import Elasticsearch
from models.pipeline.source.work import SourceWork
from pydantic import BaseModel

ES_BULK_INDEX_BATCH_SIZE = 10_000

class TransformationError(BaseModel):
    work_id: str
    stage: str
    detail: str


class BaseSource:
    def stream_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to an entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()
        
        self.processed_ids = set()
        self.errors: list[TransformationError] = []

    def add_error(self, exception: Exception | dict, stage: str, work_id: str) -> None:
        error = TransformationError(stage=stage, work_id=work_id, detail=str(exception)[:500])
        self.errors.append(error)

    def transform(self, raw_node: Any) -> SourceWork | None:
        """Accepts a raw work outputted by an adapter and returns a source work as a Pydantic model."""
        raise NotImplementedError(
            "Each transformer must implement a `transform` method."
        )

    def _stream_works(self) -> Generator[SourceWork]:
        """
        Extracts work documents from the specified source and transforms them. The `source` must define
        a `stream_raw` method.
        """        
        raw_works = self.source.stream_raw()
        for batch in batched(raw_works, 10_000):
            transformed = list(self.transform(batch))
            print(f"Successfully transformed {len(transformed)} works from a batch of {(len(batch))}...")
            
            yield from transformed

    def _generate_bulk_load_actions(
        self, records: Iterable[SourceWork], index_name: str
    ) -> Generator[dict[str, Any]]:
        for record in records:
            yield {
                "_index": index_name,
                "_id": record.state.id(),
                "_source": record.model_dump(),
            }

    def stream_to_index(self, es_client: Elasticsearch, index_name: str):
        transformed = self._stream_works()
        actions = self._generate_bulk_load_actions(transformed, index_name)
        
        for batch in batched(actions, ES_BULK_INDEX_BATCH_SIZE):
            success_count, es_errors = elasticsearch.helpers.bulk(
                es_client,
                batch,
                raise_on_error=False,
                stats_only=False,
            )
            
            # Since we called `bulk` with `stats_only=False`, we know that es_errors is a list of dicts 
            es_errors = cast(list[dict[str, Any]], es_errors)

            print(f"Successfully indexed {success_count} documents from a batch of {len(batch)}...")
            for e in es_errors:
                self.add_error(e, "index", e["index"]["_id"])

