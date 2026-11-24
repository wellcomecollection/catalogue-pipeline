"""Rewrites the works_identified table without the rows a later write superseded."""

import os
import uuid
from collections.abc import Iterator
from typing import Any, cast

import backoff
import numpy as np
import polars as pl
import pyarrow as pa
import pyarrow.parquet as pq
import structlog
from pydantic import BaseModel
from pyiceberg.exceptions import CommitFailedException
from pyiceberg.io.pyarrow import (
    compute_statistics_plan,
    data_file_statistics_from_parquet_metadata,
    parquet_path_to_id_mapping,
    schema_to_pyarrow,
)
from pyiceberg.manifest import DataFile, DataFileContent, FileFormat
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg_lookup import latest_row_index
from id_minter.schemata import WORKS_IDENTIFIED_CURRENT_ROW_ORDER

logger = structlog.get_logger(__name__)

# The table is pruned once this share of its rows is superseded.
SUPERSEDED_THRESHOLD = float(os.getenv("PRUNER_SUPERSEDED_THRESHOLD", "0.05"))

# Memory follows the rows held at once, not the size of the table. A row's
# document averages about 10 KB.
ROWS_PER_BATCH = 1_000
ROWS_PER_ROW_GROUP = 10_000

# A document is unique to its row, and a dictionary of them is held in full by
# every reader that matches one row of the file.
DICTIONARY_COLUMNS = ["type", "source_identifier_type"]

# A run that gives up discards the files it copied, so this is longer than the
# id minter's two minutes.
COMMIT_BACKOFF_MAX_TIME = 300
COMMIT_BACKOFF_MAX_INTERVAL = 10


class PruneResult(BaseModel):
    pruned: bool
    # As of the snapshot the run read, less the rows it removed.
    row_count: int
    superseded_row_count: int
    rewritten_file_count: int = 0
    removed_row_count: int = 0


class FileReplaced(Exception):
    """Another writer replaced a file the pruner read."""


def prune(table: IcebergTable, threshold: float = SUPERSEDED_THRESHOLD) -> PruneResult:
    """Replaces every data file holding superseded rows with a copy without them,
    when their share of the table reaches the threshold."""
    snapshot = table.current_snapshot()
    if snapshot is None:
        return PruneResult(pruned=False, row_count=0, superseded_row_count=0)

    files = _data_files(table, snapshot.snapshot_id)
    rows = _read_row_keys(table, files)
    current = rows[latest_row_index(rows, "id", WORKS_IDENTIFIED_CURRENT_ROW_ORDER)]
    row_count = rows.height
    superseded = row_count - current.height
    result = PruneResult(
        pruned=False, row_count=row_count, superseded_row_count=superseded
    )
    if superseded == 0 or superseded < threshold * row_count:
        logger.info("Not enough superseded rows to prune", **result.model_dump())
        return result

    replaced: list[DataFile] = []
    added: list[DataFile] = []
    for index, file in enumerate(files):
        kept = current.filter(pl.col("file") == index)["position"].to_numpy()
        if len(kept) == file.record_count:
            continue
        replaced.append(file)
        if len(kept) > 0:
            added.append(_rewrite_file(table, file, kept))

    # A run that is left writes nothing to the table. The files it wrote are
    # unreferenced, and S3 Tables removes them.
    try:
        _commit(table, files, replaced, added)
    except FileReplaced:
        logger.warning("A file was replaced during the rewrite, leaving this run")
        return result
    except CommitFailedException:
        logger.warning("The commit kept being refused, leaving this run")
        return result

    result = PruneResult(
        pruned=True,
        row_count=current.height,
        superseded_row_count=0,
        rewritten_file_count=len(replaced),
        removed_row_count=superseded,
    )
    logger.info("Pruned superseded rows", **result.model_dump())
    return result


def _data_files(table: IcebergTable, snapshot_id: int) -> list[DataFile]:
    files = []
    for task in table.scan(snapshot_id=snapshot_id).plan_files():
        if task.delete_files:
            raise ValueError(f"{'.'.join(table.name())} has delete files")
        files.append(task.file)
    return files


def _open(table: IcebergTable, file: DataFile) -> pa.NativeFile:
    return cast(pa.NativeFile, table.io.new_input(file.file_path).open())


def _read_row_keys(table: IcebergTable, files: list[DataFile]) -> pl.DataFrame:
    """The id and ordering columns of every row, with the file it is in and its
    position there."""
    frames = []
    for index, file in enumerate(files):
        with _open(table, file) as source:
            keys = pq.ParquetFile(source).read(
                columns=["id", *WORKS_IDENTIFIED_CURRENT_ROW_ORDER]
            )
        frame = pl.DataFrame(keys).with_row_index("position")
        frames.append(frame.with_columns(file=pl.lit(index)))
    return pl.concat(frames)


def _rewrite_file(table: IcebergTable, file: DataFile, kept: np.ndarray) -> DataFile:
    """Writes the kept rows of a data file to a new one, in their original order."""
    keep = np.zeros(file.record_count, dtype=bool)
    keep[kept] = True
    schema = schema_to_pyarrow(table.schema())
    path = table.location_provider().new_data_location(f"{uuid.uuid4()}-pruned.parquet")
    output = table.io.new_output(path)

    with (
        _open(table, file) as source,
        output.create(overwrite=True) as target,
        pq.ParquetWriter(
            cast(pa.NativeFile, target),
            schema,
            compression="zstd",
            use_dictionary=DICTIONARY_COLUMNS,  # type: ignore[arg-type]
        ) as writer,
    ):
        for row_group in _kept_row_groups(pq.ParquetFile(source), keep):
            writer.write_table(row_group.cast(schema))

    statistics = data_file_statistics_from_parquet_metadata(
        parquet_metadata=writer.writer.metadata,
        stats_columns=compute_statistics_plan(table.schema(), table.properties),
        parquet_column_mapping=parquet_path_to_id_mapping(table.schema()),
    )
    return DataFile.from_args(
        content=DataFileContent.DATA,
        file_path=path,
        file_format=FileFormat.PARQUET,
        partition=file.partition,
        file_size_in_bytes=len(output),
        sort_order_id=file.sort_order_id,
        spec_id=file.spec_id,
        equality_ids=None,
        key_metadata=None,
        **statistics.to_serialized_dict(),
    )


def _kept_row_groups(source: pq.ParquetFile, keep: np.ndarray) -> Iterator[pa.Table]:
    """The kept rows of a file, read a batch at a time and gathered into row groups."""
    position = 0
    pending: list[pa.RecordBatch] = []
    pending_rows = 0
    for batch in source.iter_batches(batch_size=ROWS_PER_BATCH):
        kept = batch.filter(pa.array(keep[position : position + batch.num_rows]))
        position += batch.num_rows
        pending.append(kept)
        pending_rows += kept.num_rows
        if pending_rows >= ROWS_PER_ROW_GROUP:
            yield pa.Table.from_batches(pending)
            pending = []
            pending_rows = 0
    if pending_rows > 0:
        yield pa.Table.from_batches(pending)


def _on_commit_backoff(backoff_details: Any) -> None:
    logger.warning(
        "Iceberg commit lost to another writer, retrying",
        elapsed_seconds=round(backoff_details["elapsed"]),
        tries=backoff_details["tries"],
    )


@backoff.on_exception(
    backoff.expo,
    CommitFailedException,
    factor=0.5,
    max_time=COMMIT_BACKOFF_MAX_TIME,
    max_value=COMMIT_BACKOFF_MAX_INTERVAL,
    on_backoff=_on_commit_backoff,
)
def _commit(
    table: IcebergTable,
    read: list[DataFile],
    replaced: list[DataFile],
    added: list[DataFile],
) -> None:
    """Swaps the replaced files for the added ones in the table's current snapshot.

    Files appended since the pruner read the table are left in place. Raises
    FileReplaced when a file the pruner read is no longer in the table: the rows
    it kept may be gone, and pyiceberg would add the new files beside whatever
    replaced an old one.
    """
    table.refresh()
    current = {task.file.file_path for task in table.scan().plan_files()}
    if any(file.file_path not in current for file in read):
        raise FileReplaced()

    with (
        table.transaction() as transaction,
        transaction.update_snapshot().overwrite() as update,
    ):
        for file in replaced:
            update.delete_data_file(file)
        for file in added:
            update.append_data_file(file)
