"""IdMintingTransformer: fetch works from ES, embed canonical IDs, index downstream."""

from __future__ import annotations

from collections.abc import Generator, Iterable
from itertools import batched
from typing import Any

import structlog

from core.transformer import ElasticBaseTransformer
from id_minter.embedder import (
    embed_canonical_ids,
    extract_source_identifiers,
    process_work,
)
from id_minter.id_minting_source import IdMintingSource
from id_minter.models.identifier import IdResolver, MintRequest, SourceIdentifierKey
from models.pipeline.identifier import SourceIdentifier

logger = structlog.get_logger(__name__)


# Number of works to mint canonical IDs for in a single resolver transaction.
# Balances DB round-trip amortisation against transaction footprint.
DEFAULT_MINT_BATCH_SIZE = 500


# Per-work data carried through the batched mint pipeline:
# (row_id, raw_doc, mint_requests) where mint_requests is the list returned by
# extract_source_identifiers(raw_doc).
_WorkEntry = tuple[str, dict, list[MintRequest]]


class IdMintingTransformer(ElasticBaseTransformer):
    """Fetches work documents, embeds canonical IDs via an IdResolver, and indexes them.

    Uses ``IdMintingSource`` to fetch documents from the works-source index
    based on requested mode (IDs, window, or full), runs each through the
    embedder to mint and embed canonical IDs, and writes to the target index.

    Mint requests across multiple works are batched into a single
    ``resolver.mint_ids`` call (one DB transaction) per ``mint_batch_size``
    works to amortise DB round-trip latency. If a batched mint call fails, the
    transformer falls back to per-work minting for that chunk so a single bad
    work cannot poison its neighbours.
    """

    def __init__(
        self,
        minting_source: IdMintingSource,
        resolver: IdResolver,
        mint_batch_size: int = DEFAULT_MINT_BATCH_SIZE,
    ):
        super().__init__()
        self.source = minting_source
        self.resolver = resolver
        self.mint_batch_size = mint_batch_size

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[tuple[str, dict]]:
        for chunk in batched(raw_nodes, self.mint_batch_size):
            yield from self._transform_chunk(chunk)

    def _transform_chunk(self, raw_docs: Iterable[dict]) -> Generator[tuple[str, dict]]:
        works_in_batch: list[_WorkEntry] = []
        for raw_doc in raw_docs:
            try:
                si = SourceIdentifier.model_validate(
                    raw_doc["state"]["sourceIdentifier"]
                )
            except Exception as e:
                self._add_error(e, "extract_id", str(raw_doc.get("state", {})))
                continue

            try:
                mint_requests = extract_source_identifiers(raw_doc)
            except Exception as e:
                self._add_error(e, "embed", str(si))
                continue

            works_in_batch.append((str(si), raw_doc, mint_requests))

        if not works_in_batch:
            return

        combined_requests: list[MintRequest] = []
        for _, _, mint_requests in works_in_batch:
            combined_requests.extend(mint_requests)

        # The resolver assumes a 1:1 mapping between sourceIdentifier and
        # predecessorIdentifier within a single mint_ids call (the dict
        # comprehension that builds `predecessors` is keyed by source id and
        # would silently drop conflicts). That invariant is guaranteed by the
        # source data within a single work, but batching across works extends
        # it to "across all works in a chunk". If two works in this chunk
        # disagree on the predecessor for the same source id, fall back to
        # per-work minting so each work gets its own transaction and the
        # resolver's intra-work invariant holds.
        seen_predecessors: dict[SourceIdentifierKey, SourceIdentifierKey | None] = {}
        for key, predecessor in combined_requests:
            if key in seen_predecessors and seen_predecessors[key] != predecessor:
                logger.warning(
                    "Conflicting predecessors for source id across works in chunk;"
                    " falling back to per-work minting",
                    source_id=str(key),
                    works_in_batch=len(works_in_batch),
                )
                yield from self._transform_per_work(works_in_batch)
                return
            seen_predecessors[key] = predecessor

        try:
            found = self.resolver.mint_ids(combined_requests)
        except Exception:
            # Preserve per-work error isolation: a failed batch (e.g. a single
            # work with a missing predecessor) shouldn't fail every other work
            # in the chunk. Fall back to one transaction per work; the resolver
            # has already rolled back so the connection is in a clean state.
            logger.warning(
                "Batch mint failed; falling back to per-work minting",
                works_in_batch=len(works_in_batch),
                source_identifiers=len(combined_requests),
                exc_info=True,
            )
            yield from self._transform_per_work(works_in_batch)
            return

        id_map = found

        logger.info(
            "Batch minted canonical IDs",
            works_in_batch=len(works_in_batch),
            source_identifiers=len(combined_requests),
            ids_embedded=len(id_map),
        )

        for row_id, raw_doc, _ in works_in_batch:
            try:
                embedded = embed_canonical_ids(raw_doc, id_map)
            except Exception as e:
                self._add_error(e, "embed", row_id)
                continue
            yield row_id, embedded

    def _transform_per_work(
        self, works_in_batch: list[_WorkEntry]
    ) -> Generator[tuple[str, dict]]:
        for row_id, raw_doc, _ in works_in_batch:
            try:
                embedded = process_work(raw_doc, self.resolver)
            except Exception as e:
                self._add_error(e, "embed", row_id)
                continue
            yield row_id, embedded

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
