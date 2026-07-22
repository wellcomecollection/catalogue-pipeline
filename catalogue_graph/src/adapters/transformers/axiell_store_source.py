from collections.abc import Generator
from typing import Any

from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.utils.axiell_changeset_reader import AxiellChangesetReader


class AxiellStoreSource(AdapterStoreSource):
    """Adapts an AxiellChangesetReader to the transformer source interface.

    Interleaves the reader's two streams into the single dict stream that
    `stream_to_index` batches: adapter rows first, then deletion facts as
    dicts carrying a `guid` key (the superseded guid), which adapter rows
    never do; `AxiellTransformer._transform_row` discriminates on that key.
    That dict shape is a private convention of this module — other consumers
    should use the reader's typed `iter_deletions()` directly.
    """

    def __init__(self, reader: AxiellChangesetReader):
        super().__init__(reader.adapter_store, reader.changeset_ids, reader.snapshot_id)
        self.reader = reader

    def stream_raw(self) -> Generator[dict[str, Any]]:
        yield from self.reader.iter_records()
        for deletion in self.reader.iter_deletions():
            yield {
                "id": deletion.fact_id,
                "guid": deletion.guid,
                "last_modified": deletion.last_modified,
            }
