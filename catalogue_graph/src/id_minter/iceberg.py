"""Append-only writes of identified works to the works_identified Iceberg table."""

from datetime import UTC, datetime

import backoff
import pyarrow as pa
import structlog
from pyiceberg.exceptions import CommitFailedException
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.table import Table as IcebergTable

from core.document import Document
from core.sinks import WriteResult
from merger.schemata import works_identified_row

logger = structlog.get_logger(__name__)

# Concurrent minter partitions race on the table commit and the loser retries against
# the new snapshot. Appends never conflict on data, so a few attempts are enough.
MAX_COMMIT_ATTEMPTS = 4


class IcebergSink:
    """Appends every document it is given; the table is never updated in place.

    The table is experimental, so a failed append is logged and never fails the run.
    """

    def __init__(self, table: IcebergTable):
        self.table = table

    def write(self, documents: list[Document]) -> WriteResult:
        try:
            append_identified_works(self.table, [d.body for d in documents])
        except Exception:
            logger.exception("Iceberg append failed", documents=len(documents))
            return WriteResult()
        return WriteResult(accepted=list(documents))


def append_identified_works(table: IcebergTable, documents: list[dict]) -> int:
    """Append one row per document and return how many were written.

    Rows are never updated, so writes from overlapping runs cannot lose each other.
    """
    if not documents:
        return 0

    written = datetime.now(UTC)
    rows = pa.Table.from_pylist(
        [works_identified_row(document, written) for document in documents],
        schema=schema_to_pyarrow(table.schema()),
    )
    _commit(table, rows)
    logger.info("Appended to Iceberg", rows=rows.num_rows)
    return rows.num_rows


@backoff.on_exception(
    backoff.expo, CommitFailedException, max_tries=MAX_COMMIT_ATTEMPTS, factor=0.2
)
def _commit(table: IcebergTable, rows: pa.Table) -> None:
    """Commit against the table's current snapshot; a lost race is retried after a refresh."""
    table.refresh()
    table.append(rows)
