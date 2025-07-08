from pyiceberg.catalog import load_catalog
from schemata import SCHEMA, ARROW_SCHEMA

import pyarrow.compute as pc

from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

import pyarrow as pa
import uuid

from pyiceberg.expressions import Not, IsNull
from datetime import datetime, timezone


def get_table(catalogue_name, catalogue_uri, catalogue_warehouse, catalogue_namespace, table_name):
    catalogue = load_catalog(
        catalogue_name,
        uri=catalogue_uri,
        warehouse=catalogue_warehouse,
    )
    catalogue.create_namespace_if_not_exists(catalogue_namespace)
    table_fullname = f"{catalogue_namespace}.{table_name}"
    table = catalogue.create_table_if_not_exists(
        identifier=table_fullname, schema=SCHEMA
    )
    return table


def update_table(table: IcebergTable, new_data: pa.Table, record_namespace: str):
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
    # records that have already been "deleted" do not need to be deleted again.

    existing_data = (
        table.scan(
            row_filter=Not(IsNull("content")),
            selected_fields=["namespace", "id", "content"],
        )
        .to_arrow()
        .cast(ARROW_SCHEMA)
    )
    if existing_data:
        # Although upsert takes care of the what-to-do aspect
        # of create vs update, we need to produce a changeset
        # consisting of only those records we wish to modify.
        # This allows us to tell iceberg that all of the entries
        # being changed are part of the same changeset.

        # Delete is obvious, as what we have to do here is create
        # a deletion record with which to overwrite the existing one.
        deletes = _get_deletes(existing_data, new_data, record_namespace)
        # pyiceberg offers a built-in feature that returns the update rows
        # by comparing two tables.  Unfortunately, it does not say anything
        # about inserts.
        updates = _find_updates(existing_data, new_data)
        inserts = _find_inserts(existing_data, new_data, record_namespace)
        changeset = pa.concat_tables([deletes, updates, inserts])
    else:
        # Empty DB short-circuit, just write it all in.
        changeset = new_data
    if changeset:
        now = pa.scalar(datetime.now(timezone.utc), pa.timestamp("us", "UTC"))
        changeset_id = str(uuid.uuid1())
        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            [[changeset_id] * len(changeset)],
        ).append_column(
            pa.field("last_modified", type=pa.timestamp("us", "UTC"), nullable=True),
            [[now] * len(changeset)],
        )
        table.upsert(changeset, ["id"])
        return changeset_id
    else:
        print("nothing to do")
        return None


def _find_updates(existing_data: pa.Table, new_data: pa.Table):
    return get_rows_to_update(new_data, existing_data, ["id"])


def _find_inserts(existing_data: pa.Table, new_data: pa.Table, record_namespace: str):
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
        (pc.field("namespace") == record_namespace) & ~pc.field("id").isin(new_ids)
    ).column("id")
    return pa.Table.from_pylist(
        [{"namespace": record_namespace, "id": id.as_py()} for id in missing_ids],
        schema=ARROW_SCHEMA,
    )
