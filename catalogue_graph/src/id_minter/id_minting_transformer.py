"""IdMintingTransformer: fetch works from ES, embed canonical IDs, index downstream."""

from __future__ import annotations

from collections.abc import Generator, Iterable
from itertools import batched
from typing import Any

import structlog

from core.document import Document
from core.transformer import BatchTransformer
from id_minter.embedder import embed_canonical_ids, extract_source_identifiers
from id_minter.id_minting_source import IdMintingSource
from id_minter.models.identifier import IdResolver, MintRequest
from models.pipeline.identifier import SourceIdentifier
from utils.elasticsearch import version_from_modified_time

logger = structlog.get_logger(__name__)


# Number of works to mint canonical IDs for in a single resolver transaction.
# Balances DB round-trip amortisation against transaction footprint.
DEFAULT_MINT_BATCH_SIZE = 500


# Per-work data carried through the batched mint pipeline:
# (row_id, raw_doc, version, mint_requests) where mint_requests is the list
# returned by extract_source_identifiers(raw_doc).
_WorkEntry = tuple[str, dict, int, list[MintRequest]]


def document_version(raw_doc: dict) -> int:
    """Guards index writes on source time, so an overlapping run cannot overwrite a newer copy."""
    return version_from_modified_time(raw_doc["state"]["sourceModifiedTime"])


class IdMintingTransformer(BatchTransformer):
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

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[Document]:
        for chunk in batched(raw_nodes, self.mint_batch_size):
            yield from self._transform_chunk(chunk)

    def _transform_chunk(self, raw_docs: Iterable[dict]) -> Generator[Document]:
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
                version = document_version(raw_doc)
            except Exception as e:
                self._add_error(e, "version", str(si))
                continue

            try:
                mint_requests = extract_source_identifiers(raw_doc)
            except Exception as e:
                self._add_error(e, "embed", str(si))
                continue

            works_in_batch.append((str(si), raw_doc, version, mint_requests))

        if not works_in_batch:
            return

        try:
            yield from self._mint_and_embed(works_in_batch)
        except Exception:
            # Preserve per-work error isolation: a failed batch (e.g. a single
            # work with a missing predecessor, or two works in this chunk
            # disagreeing on the predecessor for a shared source id) shouldn't
            # fail every other work in the chunk. Fall back to one transaction
            # per work; the resolver has already rolled back so the connection
            # is in a clean state.
            logger.warning(
                "Batch mint failed; falling back to per-work minting",
                works_in_batch=len(works_in_batch),
                exc_info=True,
            )
            for work in works_in_batch:
                row_id = work[0]
                try:
                    yield from self._mint_and_embed([work])
                except Exception as e:
                    self._add_error(e, "embed", row_id)

    def _mint_and_embed(self, works: list[_WorkEntry]) -> Generator[Document]:
        """Mint canonical IDs for ``works`` in a single resolver call, then embed.

        Used by both the batch path (whole chunk) and the per-work fallback
        path (one work at a time). Raises whatever ``resolver.mint_ids``
        raises so callers can decide whether to fall back; ``embed_canonical_ids``
        errors are reported per-work via ``self._add_error``.
        """
        combined_requests: list[MintRequest] = []
        for _, _, _, mint_requests in works:
            combined_requests.extend(mint_requests)

        id_map = self.resolver.mint_ids(combined_requests)

        logger.info(
            "Minted canonical IDs",
            works=len(works),
            source_identifiers=len(combined_requests),
            ids_embedded=len(id_map),
        )

        for row_id, raw_doc, version, _ in works:
            try:
                embedded = embed_canonical_ids(raw_doc, id_map)
            except Exception as e:
                self._add_error(e, "embed", row_id)
                continue
            yield Document(
                source_id=row_id,
                target_id=str(embedded["state"]["canonicalId"]),
                body=embedded,
                version=version,
            )
