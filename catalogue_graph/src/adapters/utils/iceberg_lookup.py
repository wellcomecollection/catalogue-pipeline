"""Fetches rows of an Iceberg table by key without decoding the whole table."""

from collections.abc import Iterable, Sequence

import polars as pl
from pyiceberg.table import Table as IcebergTable


class IcebergTableLookup:
    """Reads rows by key with a filtered polars scan over a snapshot's data files.

    Polars reads the files directly. Where they are sorted by the key and carry a
    page index, as compacted S3 Tables files are, a lookup decodes only the pages
    holding the wanted keys. Delete files are not applied, so the table must be
    append-only.
    """

    def __init__(self, files: Sequence[str], key: str) -> None:
        self.files = list(files)
        self.key = key

    @classmethod
    def for_snapshot(
        cls, table: IcebergTable, key: str, snapshot_id: int | None = None
    ) -> "IcebergTableLookup":
        files = []
        for task in table.scan(snapshot_id=snapshot_id).plan_files():
            if task.delete_files:
                raise ValueError(f"{'.'.join(table.name())} has delete files")
            files.append(task.file.file_path.removeprefix("file://"))
        return cls(files, key)

    def fetch(
        self,
        keys: Iterable[str],
        columns: Sequence[str],
        latest_by: Sequence[str] = (),
    ) -> pl.DataFrame:
        """The wanted rows with the given columns.

        With `latest_by`, one row per key: the one sorting highest on those columns.
        """
        needed = list(dict.fromkeys([self.key, *columns, *latest_by]))
        rows = (
            pl.scan_parquet(self.files)
            .select(needed)
            .filter(pl.col(self.key).is_in(list(keys)))
            .collect(engine="streaming")
        )
        if latest_by:
            # Picked on the narrow columns, then gathered: sorting the whole rows
            # costs more than reading them.
            current = (
                rows.select(self.key, *latest_by)
                .with_row_index()
                .sort(latest_by, descending=True)
                .unique(self.key, keep="first")
            )
            rows = rows[current["index"]]
        return rows.select(columns)
