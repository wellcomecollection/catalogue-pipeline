"""The works_identified Iceberg table and append-only writes of identified works to it."""

import os
from datetime import UTC, datetime
from typing import Any

import backoff
import pyarrow as pa
import structlog
from pyiceberg.exceptions import CommitFailedException
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
    get_local_table,
    get_rest_api_table,
)
from core.document import Document
from core.sinks import WriteResult
from id_minter.schemata import (
    WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    WORKS_IDENTIFIED_SORT_ORDER,
    works_identified_row,
)

logger = structlog.get_logger(__name__)

AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")

REST_API_ICEBERG_CONFIG = RestApiIcebergTableConfig(
    table_name=os.getenv("WORKS_IDENTIFIED_TABLE_NAME", "works_identified"),
    namespace=os.getenv("WORKS_IDENTIFIED_NAMESPACE", "wellcomecollection_catalogue"),
    iceberg_schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    sort_order=WORKS_IDENTIFIED_SORT_ORDER,
    s3_tables_bucket=os.getenv(
        "S3_TABLES_BUCKET", "wellcomecollection-platform-catalogue-pipeline"
    ),
    region=AWS_REGION,
    account_id=AWS_ACCOUNT_ID,
)

LOCAL_ICEBERG_CONFIG = LocalIcebergTableConfig(
    table_name=os.getenv("LOCAL_WORKS_IDENTIFIED_TABLE_NAME", "works_identified"),
    namespace=os.getenv("LOCAL_WORKS_IDENTIFIED_NAMESPACE", "matcher"),
    iceberg_schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    sort_order=WORKS_IDENTIFIED_SORT_ORDER,
    db_name=os.getenv("LOCAL_DB_NAME", "matcher_catalog"),
)


def get_works_identified_table(
    use_rest_api_table: bool, create_if_not_exists: bool = False
) -> IcebergTable:
    if use_rest_api_table:
        return get_rest_api_table(REST_API_ICEBERG_CONFIG, create_if_not_exists)

    return get_local_table(LOCAL_ICEBERG_CONFIG, create_if_not_exists)


# Concurrent minter partitions race on the table commit and the loser retries against
# the new snapshot. The budget covers queuing behind the other partitions, well inside the Lambda timeout.
COMMIT_BACKOFF_MAX_TIME = 120
COMMIT_BACKOFF_MAX_INTERVAL = 10


class IcebergSink:
    """Appends every document it is given; the table is never updated in place.

    The table is experimental, so a failed append is logged and never fails the run.
    """

    def __init__(self, table: IcebergTable):
        self.table = table

    def write(self, documents: list[Document]) -> WriteResult:
        try:
            append_identified_works(self.table, documents)
        except Exception:
            logger.exception("Iceberg append failed", documents=len(documents))
            return WriteResult()
        return WriteResult(accepted=list(documents))


def append_identified_works(table: IcebergTable, documents: list[Document]) -> int:
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
def _commit(table: IcebergTable, rows: pa.Table) -> None:
    """Commit against the table's current snapshot; a lost race is retried after a refresh."""
    table.refresh()
    table.append(rows)
