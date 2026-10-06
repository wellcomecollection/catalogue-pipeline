from collections.abc import Generator, Iterable
from dataclasses import dataclass, field
from itertools import batched
from typing import Any

import structlog
from pydantic import BaseModel

from core.document import Document
from core.sinks import Sink
from core.source import BaseSource

logger = structlog.get_logger(__name__)

BATCH_SIZE = 10_000
# Only the first errors are kept, to cap manifest file sizes.
MAX_ERRORS = 1_000


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()


class TransformationError(BaseModel):
    row_id: str
    stage: str
    detail: str


@dataclass
class SinkResult:
    """What one sink made of a run. Errors include records that failed to transform,
    since those reached no sink."""

    accepted_ids: list[str] = field(default_factory=list)
    # Rejected because the sink already held a newer copy; no retry needed.
    superseded_ids: list[str] = field(default_factory=list)
    # Rejected by the sink. Unlike `errors`, never capped.
    failed_ids: list[str] = field(default_factory=list)
    errors: list[TransformationError] = field(default_factory=list)


def _error(exception: Exception | dict, stage: str, row_id: str) -> TransformationError:
    return TransformationError(stage=stage, row_id=row_id, detail=str(exception)[:500])


class BatchTransformer(BaseTransformer):
    """Transforms a source's records into documents in batches and writes each batch to sinks."""

    def __init__(self) -> None:
        super().__init__()
        self._errors: list[TransformationError] = []
        self._error_row_ids: set[str] = set()

    def _add_error(self, exception: Exception | dict, stage: str, row_id: str) -> None:
        if len(self._errors) < MAX_ERRORS and row_id not in self._error_row_ids:
            self._error_row_ids.add(row_id)
            self._errors.append(_error(exception, stage, row_id))

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[Document]:
        """Transform a batch of raw items into documents."""
        raise NotImplementedError(
            "Each transformer must implement a `transform` method."
        )

    def _transform_batches(self) -> Generator[list[Document]]:
        """
        Extracts documents from the specified source and transforms them. The `source` must define
        a `stream_raw` method.
        """
        raw_works = self.source.stream_raw()
        for raw_batch in batched(raw_works, BATCH_SIZE):
            transformed_batch = list(self.transform(raw_batch))
            logger.info(
                "Transformed batch",
                transformed_count=len(transformed_batch),
                batch_size=len(raw_batch),
            )
            yield transformed_batch

    def stream_to(self, sink: Sink) -> SinkResult:
        """Write every batch to the sink and return its result."""
        (result,) = self.stream_to_many(sink)
        return result

    def stream_to_many(self, *sinks: Sink) -> list[SinkResult]:
        """Write every batch to each sink and return one result per sink."""
        self._errors, self._error_row_ids = [], set()
        results = [SinkResult() for _ in sinks]

        for transformed_batch in self._transform_batches():
            for sink, result in zip(sinks, results, strict=True):
                written = sink.write(transformed_batch)
                result.accepted_ids.extend(d.target_id for d in written.accepted)
                result.superseded_ids.extend(d.target_id for d in written.superseded)
                result.failed_ids.extend(d.target_id for d, _ in written.failed)
                for document, error in written.failed:
                    if len(result.errors) < MAX_ERRORS:
                        result.errors.append(_error(error, "index", document.source_id))
                if written.failed:
                    logger.warning(
                        "Writes failed",
                        sink=type(sink).__name__,
                        count=len(written.failed),
                    )
                if written.superseded:
                    logger.warning(
                        "Skipped documents already at a newer version",
                        sink=type(sink).__name__,
                        count=len(written.superseded),
                    )

        for result in results:
            result.errors = (self._errors + result.errors)[:MAX_ERRORS]
        return results
