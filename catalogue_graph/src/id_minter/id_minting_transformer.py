"""IdMintingTransformer: fetch works from ES, embed canonical IDs, index downstream."""

from __future__ import annotations

from collections.abc import Generator, Iterable
from typing import Any

import structlog

from core.transformer import ElasticBaseTransformer
from id_minter.embedder import process_work
from id_minter.id_minting_source import IdMintingSource
from id_minter.models.identifier import IdResolver
from models.pipeline.identifier import SourceIdentifier

logger = structlog.get_logger(__name__)


class IdMintingTransformer(ElasticBaseTransformer):
    """Fetches work documents, embeds canonical IDs via an IdResolver, and indexes them.

    Uses ``IdMintingSource`` to fetch documents from the works-source index
    based on requested mode (IDs, window, or full), runs each through the
    embedder to mint and embed canonical IDs, and writes to the target index.
    """

    def __init__(
        self,
        minting_source: IdMintingSource,
        resolver: IdResolver,
    ):
        super().__init__()
        self.source = minting_source
        self.resolver = resolver

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[tuple[str, dict]]:
        for raw_doc in raw_nodes:
            try:
                si = SourceIdentifier.model_validate(
                    raw_doc["state"]["sourceIdentifier"]
                )
            except Exception as e:
                self._add_error(e, "extract_id", str(raw_doc.get("state", {})))
                continue

            try:
                embedded = process_work(raw_doc, self.resolver)
            except Exception as e:
                self._add_error(e, "embed", str(si))
                continue

            yield str(si), embedded

    def _get_document_id(self, record: dict) -> str:
        return str(record["state"]["canonicalId"])

    def _generate_bulk_load_actions(
        self, records: Iterable[dict], index_name: str
    ) -> Generator[dict[str, Any]]:
        for record in records:
            yield {
                "_index": index_name,
                "_id": self._get_document_id(record),
                "_source": record,
            }
