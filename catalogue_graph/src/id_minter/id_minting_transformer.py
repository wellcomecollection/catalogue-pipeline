"""IdMintingTransformer: fetch works from ES, embed canonical IDs, index downstream."""

from __future__ import annotations

from collections.abc import Generator, Iterable
from typing import Any

import structlog

from core.transformer import ElasticBaseTransformer
from id_minter.embedder import process_work
from id_minter.id_minting_source import IdMintingSource
from id_minter.models.identifier import IdResolver

logger = structlog.get_logger(__name__)


def _build_source_identifier_string(source_identifier: dict) -> str:
    """Build the composite source identifier string from a sourceIdentifier dict.

    Format: ``OntologyType[identifierType/value]``
    e.g. ``Work[sierra-system-number/b1000001]``
    """
    ontology_type = source_identifier["ontologyType"]
    identifier_type = source_identifier["identifierType"]["id"]
    value = source_identifier["value"]
    return f"{ontology_type}[{identifier_type}/{value}]"


class IdMintingTransformer(ElasticBaseTransformer):
    """Fetches work documents, embeds canonical IDs via an IdResolver, and indexes them.

    Uses ``IdMintingSource`` to fetch documents from the works-source index
    based on requested mode (IDs, window, or full), runs each through the
    embedder to mint and embed canonical IDs, and writes to the target index.
    """

    def __init__(
        self,
        mintingsource: IdMintingSource,
        resolver: IdResolver,
    ):
        super().__init__()
        self.source = mintingsource
        self.resolver = resolver

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[tuple[str, dict]]:
        for raw_doc in raw_nodes:
            try:
                si = raw_doc["state"]["sourceIdentifier"]
                row_id = _build_source_identifier_string(si)
            except (KeyError, TypeError) as e:
                self._add_error(e, "extract_id", str(raw_doc.get("state", {})))
                continue

            try:
                embedded = process_work(raw_doc, self.resolver)
            except Exception as e:
                self._add_error(e, "embed", row_id)
                continue

            yield (row_id, embedded)

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
