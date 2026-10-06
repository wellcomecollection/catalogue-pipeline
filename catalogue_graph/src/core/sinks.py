"""Destinations a transformer streams its batches to."""

from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any, Protocol

import backoff
import pyarrow as pa
import structlog
from elasticsearch import Elasticsearch
from pyiceberg.exceptions import CommitFailedException
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.table import Table as IcebergTable

from core.document import Document
from utils.elasticsearch import index_es_batch, is_version_conflict

logger = structlog.get_logger(__name__)

# Concurrent writers race on the table commit and the loser retries against the new
# snapshot. The budget covers queuing behind the other writers, well inside a Lambda timeout.
COMMIT_BACKOFF_MAX_TIME = 120
COMMIT_BACKOFF_MAX_INTERVAL = 10


@dataclass
class WriteResult:
    accepted: list[Document] = field(default_factory=list)
    # Rejected because the sink already held a newer copy
    superseded: list[Document] = field(default_factory=list)
    failed: list[tuple[Document, Any]] = field(default_factory=list)


class Sink(Protocol):
    def write(self, documents: list[Document]) -> WriteResult: ...


class ElasticsearchSink(Sink):
    """Bulk-indexes documents by id. A document with a version is written with an
    `external_gte` guard, so an older copy never overwrites a newer one."""

    def __init__(self, es_client: Elasticsearch, index_name: str):
        self.es_client = es_client
        self.index_name = index_name

    def write(self, documents: list[Document]) -> WriteResult:
        actions = [self._action(document) for document in documents]
        _, errors = index_es_batch(self.es_client, actions)
        errors_by_id = {error["index"]["_id"]: error for error in errors}

        result = WriteResult()
        for document in documents:
            error = errors_by_id.get(document.target_id)
            if error is None:
                result.accepted.append(document)
            elif is_version_conflict(error):
                result.superseded.append(document)
            else:
                result.failed.append((document, error))
        return result

    def _action(self, document: Document) -> dict[str, Any]:
        action: dict[str, Any] = {
            "_index": self.index_name,
            "_id": document.target_id,
            "_source": document.body,
        }
        if document.version is not None:
            action["_version"] = document.version
            action["_version_type"] = "external_gte"
        return action


class IcebergSink(Sink):
    """Appends one row per document, built by `to_row`; the table is never updated in place.

    A failed append is logged and returned as failed documents rather than raised."""

    def __init__(
        self, table: IcebergTable, to_row: Callable[[Document, datetime], dict]
    ):
        self.table = table
        self.to_row = to_row

    def write(self, documents: list[Document]) -> WriteResult:
        if not documents:
            return WriteResult()
        try:
            self._append(documents)
        except Exception as e:
            logger.exception("Iceberg append failed", documents=len(documents))
            return WriteResult(failed=[(document, e) for document in documents])
        return WriteResult(accepted=list(documents))

    def _append(self, documents: list[Document]) -> None:
        written = datetime.now(UTC)
        rows = pa.Table.from_pylist(
            [self.to_row(document, written) for document in documents],
            schema=schema_to_pyarrow(self.table.schema()),
        )
        _commit(self.table, rows)
        logger.info("Appended to Iceberg", rows=rows.num_rows)


def _on_commit_backoff(backoff_details: Any) -> None:
    logger.warning(
        "Iceberg commit lost to another writer, retrying",
        elapsed_seconds=round(backoff_details["elapsed"]),
        tries=backoff_details["tries"],
    )


@backoff.on_exception(
    backoff.expo,
    CommitFailedException,
    factor=0.5,
    max_time=COMMIT_BACKOFF_MAX_TIME,
    max_value=COMMIT_BACKOFF_MAX_INTERVAL,
    on_backoff=_on_commit_backoff,
)
def _commit(table: IcebergTable, rows: pa.Table) -> None:
    """Commit against the table's current snapshot; a lost race is retried after a refresh."""
    table.refresh()
    table.append(rows)
