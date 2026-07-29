from collections.abc import Generator
from typing import Any

from adapters.utils.adapter_store_source import RecordSource
from adapters.utils.axiell_changeset_reader import AxiellChangesetReader


class AxiellStoreSource(RecordSource):
    """Adapts an AxiellChangesetReader to the transformer's RecordSource.

    Interleaves the reader's two streams into the single dict stream that
    `stream_to_index` batches: adapter rows first, then deletion facts as
    dicts carrying a `guid` key, which adapter rows never do;
    `AxiellTransformer._transform_row` discriminates on that key. That dict
    shape is a private convention of this module — other consumers should use
    the reader's typed `iter_deletions()` directly.
    """

    def __init__(self, reader: AxiellChangesetReader):
        self.reader = reader
        self.snapshot_id = reader.snapshot_id

    def stream_raw(self) -> Generator[dict[str, Any]]:
        for row in self.reader.iter_records():
            if "guid" in row:
                # The discriminator below relies on adapter rows never
                # carrying this key; if the adapter schema ever grows one,
                # every row would dispatch as a deletion.
                raise RuntimeError(
                    "adapter row carries a 'guid' key, which is reserved for "
                    "deletion facts in this stream"
                )
            yield row
        for deletion in self.reader.iter_deletions():
            yield {
                "id": deletion.fact_id,
                "guid": deletion.guid,
                "last_modified": deletion.last_modified,
            }
