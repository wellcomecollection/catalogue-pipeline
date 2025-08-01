from schemata import ARROW_SCHEMA
from typing import Optional

from pyiceberg.table import Table as IcebergTable

import pyarrow as pa
import uuid

from pyiceberg.expressions import In
from datetime import datetime, timezone
import pyarrow.compute as pc
from pyiceberg.table.upsert_util import get_rows_to_update


def update_table(
    table: IcebergTable, new_data: pa.Table, record_namespace: str
) -> Optional[str]:
    """
    Perform an update on `table`, using the data provided in new_data.

    This is an enhanced upsert that also:
    - "deletes" records that are absent in new_data
        - deletion involves blanking the content of a row, but leaving the id intact.
            this allows us to push the identifier of the deleted record downstream
            even if the workflow run that performs the deletion fails.
    - adds an explicit changeset identifier for all records altered in this update.
        - a normal upsert consists of separate operations, although they are wrapped in
          a transaction, and therefore atomic on write, there is no built-in, readable or queryable
           linking that joins all the operations in one transaction.
           This label allows us to read the changes as written at a later point
    """

    # Pull out a table view excluding the existing changeset column, and any deleted rows.
    # the incoming data does not know about changesets, so this allows us to perform a table comparison
    # based on the "real" data.
    existing_data = (
        table.scan(
            selected_fields=("namespace", "id", "content"),
        )
        .to_arrow()
        .cast(ARROW_SCHEMA)
    )
    if existing_data.num_rows > 0:
        existing_data = existing_data.sort_by("id")
        new_data = new_data.sort_by("id")
        # we need to produce a changeset consisting of only those
        # records we wish to modify.
        # This allows us to store a value in iceberg that identifies
        # all the entries being changed as part of the same changeset.

        # Delete is obvious, as what we have to do here is create
        # a deletion record with which to overwrite the existing one.
        deletes = _get_deletes(existing_data, new_data, record_namespace)

        # pyiceberg offers a built-in feature that returns the update rows
        # by comparing two tables.  Unfortunately, it does not say anything
        # about inserts.
        updates = _find_updates(existing_data, new_data)
        # Insert is essentially the opposite of delete. What is in new but not old.
        inserts = _find_inserts(existing_data, new_data, record_namespace)
        changes = pa.concat_tables([deletes, updates])
    else:
        # Empty DB short-circuit, just write it all in.
        inserts = new_data
        changes = None
    if changes or inserts:
        return _upsert_with_markers(table, changes, inserts)
    else:
        print("nothing to do")
        return None


def _upsert_with_markers(
    table: IcebergTable, changes: pa.Table, inserts: pa.Table
) -> str:
    """
    Insert and update records, adding the timestamp and changeset values to
    any changed rows.
    :param table: The table to update
    :param changes: New versions of existing records to change
    :param inserts: New records to insert
    """
    changeset_id = str(uuid.uuid1())
    timestamp = pa.scalar(datetime.now(timezone.utc), pa.timestamp("us", "UTC"))
    if changes:
        changes = _append_change_columns(changes, changeset_id, timestamp)
    if inserts:
        inserts = _append_change_columns(inserts, changeset_id, timestamp)
    with table.transaction() as tx:
        # Because we already know which records to overwrite and which ones to append,
        # we can avoid all the extra processing that happens inside table.upsert to find
        # matching records, check them for differences etc.
        # Just overwrite all the `changes` and append all the `inserts`
        if changes:
            overwrite_mask_predicate = _create_match_filter(changes)
            tx.overwrite(changes, overwrite_filter=overwrite_mask_predicate)
        if inserts:
            tx.append(inserts)
    return changeset_id


def _create_match_filter(changes: pa.Table) -> In:
    change_ids = changes.column("id").to_pylist()
    return In("id", change_ids)


def _append_change_columns(
    changeset: pa.Table, changeset_id: str, timestamp: pa.lib.TimestampScalar
) -> pa.Table:
    changeset = changeset.append_column(
        pa.field("changeset", type=pa.string(), nullable=True),
        [[changeset_id] * len(changeset)],
    ).append_column(
        pa.field("last_modified", type=pa.timestamp("us", "UTC"), nullable=True),
        [[timestamp] * len(changeset)],
    )

    return changeset


def _find_updates(existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
    return get_rows_to_update(new_data, existing_data, ["namespace", "id"])


def _find_inserts(
    existing_data: pa.Table, new_data: pa.Table, record_namespace: str
) -> pa.Table:
    old_ids = existing_data.column("id")
    missing_records = new_data.filter(
        (pc.field("namespace") == record_namespace) & ~pc.field("id").isin(old_ids)
    )
    return missing_records


def _get_deletes(
    existing_data: pa.Table, new_data: pa.Table, record_namespace: str
) -> pa.Table:
    """
    Find records in `existing_data` that are not in `new_data`, and produce a
    pyarrow Table that can be used to update those records by emptying their content.
    """
    new_ids = new_data.column("id")
    missing_ids = existing_data.filter(
        # records that have already been "deleted" do not need to be deleted again.
        (~pc.field("content").is_null())
        & (pc.field("namespace") == record_namespace)
        & ~pc.field("id").isin(new_ids)
    ).column("id")
    return pa.Table.from_pylist(
        [{"namespace": record_namespace, "id": id.as_py()} for id in missing_ids],
        schema=ARROW_SCHEMA,
    )
