"""Destinations a transformer streams its batches to."""

from dataclasses import dataclass, field
from typing import Any, Protocol

from elasticsearch import Elasticsearch

from core.document import Document
from utils.elasticsearch import index_es_batch, is_version_conflict


@dataclass
class WriteResult:
    accepted: list[Document] = field(default_factory=list)
    # Rejected because the sink already held a newer copy
    superseded: list[Document] = field(default_factory=list)
    failed: list[tuple[Document, Any]] = field(default_factory=list)


class Sink(Protocol):
    def write(self, documents: list[Document]) -> WriteResult: ...


class ElasticsearchSink:
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
