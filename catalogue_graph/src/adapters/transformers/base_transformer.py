from collections.abc import Generator, Iterable
from typing import Any

import elasticsearch.helpers
from elasticsearch import Elasticsearch
from models.pipeline.source.work import SourceWork
from pydantic import BaseModel


class TransformationError(BaseModel):
    work_id: str | None
    stage: str
    detail: str


class BaseSource:
    def stream_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to an entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()

    def transform(self, raw_node: Any) -> Any | None:
        """Accepts a raw node from the source dataset and returns a transformed node as a Pydantic model."""
        raise NotImplementedError(
            "Each transformer must implement a `transform` method."
        )

    def _stream_works(self, sample_size: int | None = None) -> Generator[SourceWork | TransformationError]:
        """
        Extracts work documents from the specified source and transforms them. The `source` must define
        a `stream_raw` method. Takes an optional parameter to only extract the first `number` documents.
        """
        counter = 0

        for raw_work in self.source.stream_raw():
            for transformed in self.transform(raw_work):
                yield transformed
                counter += 1
    
                if counter % 10000 == 0:
                    print(f"Transformed {counter} documents...")
                if counter == sample_size:
                    return

        print(f"Transformed all {counter} documents.")

    def _generate_bulk_load_actions(
        self, records: Iterable[SourceWork], index_name: str
    ) -> Generator[dict[str, Any]]:
        for record in records:
            yield {
                "_index": index_name,
                "_id": record.state.id(),
                "_source": record.model_dump(),
            }

    def stream_to_source_index(
        self, es_client: Elasticsearch, index_name: str, sample_size: int | None = None
    ) -> tuple[int, list]:
        # TODO: Handle errors and batching
        for item in self._stream_works(sample_size):
            if isinstance(item, TransformationError):
                print(item)
            
        actions = self._generate_bulk_load_actions(
            self._stream_works(sample_size), index_name
        )
        
        print(list(actions)[0])
        
        return
        success_count, raw_errors = elasticsearch.helpers.bulk(
            es_client,
            actions,
            raise_on_error=False,
            stats_only=False,
        )

        errors = []
        if raw_errors:
            assert isinstance(raw_errors, list)
            for err in raw_errors:
                index_error = err.get("index", {})
                errors.append(
                    {
                        "stage": "index",
                        "id": index_error.get("_id"),
                        "status": index_error.get("status"),
                        "error_type": (index_error.get("error") or {}).get("type"),
                        "raw": str(err)[:500],
                    }
                )

        return success_count, errors
