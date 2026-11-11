"""Fetches rows of an Iceberg table by key without decoding the whole table."""

from collections.abc import Iterable, Sequence
from itertools import batched

import polars as pl
from pyiceberg.table import Table as IcebergTable


class IcebergTableLookup:
    """Reads rows by key with a filtered polars scan over a snapshot's data files.

    A sizeable set of random keys reads nearly the whole table, since a file has
    only a few row groups and polars skips a row group only when its key range
    holds none of the wanted keys. What polars saves is decoding: it evaluates
    the key filter first and decodes only the pages holding matching rows, where
    a pyiceberg scan decodes every row group and filters afterwards.

    A filtered scan over small files holds far more memory than the rows it
    returns, so the files are scanned a few at a time and each result copied
    out before the next batch.

    Delete files are not applied, so the table must be append-only.
    """

    FILES_PER_SCAN = 20

    def __init__(self, files: Sequence[str], key: str) -> None:
        self.files = list(files)
        self.key = key

    @classmethod
    def for_snapshot(
        cls, table: IcebergTable, key: str, snapshot_id: int | None = None
    ) -> "IcebergTableLookup":
        if snapshot_id is not None and table.snapshot_by_id(snapshot_id) is None:
            raise ValueError(f"{'.'.join(table.name())} has no snapshot {snapshot_id}")
        files = []
        for task in table.scan(snapshot_id=snapshot_id).plan_files():
            if task.delete_files:
                raise ValueError(f"{'.'.join(table.name())} has delete files")
            files.append(task.file.file_path.removeprefix("file://"))
        if not files:
            raise ValueError(f"{'.'.join(table.name())} has no data files")
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
        wanted = list(keys)
        rows = pl.concat(
            self._scan(files, needed, wanted)
            for files in batched(self.files, self.FILES_PER_SCAN)
        )
        if latest_by:
            # Picked on the narrow columns, then gathered: sorting the whole rows
            # costs more than reading them.
            current = (
                rows.select(self.key, *latest_by)
                .with_row_index()
                .sort(latest_by, descending=True, nulls_last=True)
                .unique(self.key, keep="first")
            )
            rows = rows[current["index"]]
        return rows.select(columns)

    def _scan(
        self, files: Sequence[str], needed: list[str], wanted: list[str]
    ) -> pl.DataFrame:
        rows = (
            pl.scan_parquet(list(files))
            .select(needed)
            .filter(pl.col(self.key).is_in(wanted))
            .collect(engine="streaming")
        )
        # Copy out the matching rows so the scan's memory is released.
        return rows.select(pl.all().gather(pl.int_range(pl.len())))
