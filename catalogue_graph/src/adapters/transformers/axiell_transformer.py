from collections.abc import Generator
from typing import Any

import structlog

from adapters.transformers.axiell_store_source import AxiellStoreSource
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from adapters.transformers.builders.reconciler_work_builder import ReconcilerWorkBuilder
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.adapter_store_source import RecordSource
from adapters.utils.axiell_changeset_reader import AxiellChangesetReader
from ingestor.models.shared.deleted_reason import DeletedFromSource
from models.pipeline.source.work import SourceWork

logger = structlog.get_logger(__name__)


class AxiellTransformer(MarcXmlTransformer):
    def __init__(self, reader: AxiellChangesetReader) -> None:
        # Stored before super().__init__, which builds the source via _build_source.
        self._reader = reader
        super().__init__(
            adapter_store=reader.adapter_store,
            changeset_ids=reader.changeset_ids,
            snapshot_id=reader.snapshot_id,
        )

    def _build_source(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
        ids: list[str] | None = None,
    ) -> RecordSource:
        # ids is not yet wired for Axiell; build_transformer rejects it before
        # an AxiellTransformer is ever constructed with one.
        return AxiellStoreSource(self._reader)

    @property
    def work_builder(self) -> type[AxiellWorkBuilder]:
        return AxiellWorkBuilder

    def _transform_row(self, row: dict[str, Any]) -> Generator[tuple[str, SourceWork]]:
        # Deletion facts (appended by AxiellStoreSource) carry a `guid` key;
        # adapter rows never do. Each fact tombstones its superseded guid.
        if "guid" in row:
            # Record failures in the manifest like MARC row failures, so one
            # bad fact cannot fail the whole transform run.
            try:
                builder = ReconcilerWorkBuilder(row["guid"], row["last_modified"])
                yield (
                    row["id"],
                    builder.transform_deleted_work(deleted_reason=DeletedFromSource()),
                )
            except Exception as e:
                logger.error(
                    "Error transforming deletion fact", row_id=row["id"], error=str(e)
                )
                self._add_error(e, "transform", row["id"])
        else:
            yield from super()._transform_row(row)
