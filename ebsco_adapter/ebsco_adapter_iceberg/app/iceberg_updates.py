from pyiceberg.catalog import load_catalog
from schemata import SCHEMA, ARROW_SCHEMA

from pyiceberg.table import Table as IcebergTable

import pyarrow as pa
import uuid

from pyiceberg.expressions import Not, IsNull, In
from datetime import datetime, timezone
import pyarrow.compute as pc
from hashlib import sha3_256
from timeit import timeit
import functools

import operator


def get_table(
        catalogue_name, catalogue_uri, catalogue_warehouse, catalogue_namespace, table_name
) -> IcebergTable:
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
    new_data = update_content_hash(new_data)
    existing_data = (
        table.scan(
            row_filter=Not(IsNull("content")),
            selected_fields=("namespace", "id", "content", "content_hash"),
        )
        .to_arrow()
        .cast(ARROW_SCHEMA)
    )
    if existing_data:
        existing_data = existing_data.sort_by("id")
        new_data = new_data.sort_by("id")
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


@timeit
def _upsert_with_markers(table: IcebergTable, changes: pa.Table, inserts: pa.Table) -> str:
    changeset_id = str(uuid.uuid1())
    timestamp = pa.scalar(datetime.now(timezone.utc), pa.timestamp("us", "UTC"))
    if changes:
        changes = _append_change_columns(changes, changeset_id, timestamp)
    if inserts:
        inserts = _append_change_columns(inserts, changeset_id, timestamp)
    with table.transaction() as tx:
        if changes:
            overwrite_mask_predicate = _create_match_filter(changes)
            tx.overwrite(changes, overwrite_filter=overwrite_mask_predicate)
        if inserts:
            tx.append(inserts)
    return changeset_id


def _create_match_filter(changes: pa.Table):
    change_ids = changes.column("id").to_pylist()
    return In("id", change_ids)


@timeit
def _append_change_columns(changeset: pa.Table, changeset_id: str, timestamp: pa.lib.TimestampScalar):
    changeset = changeset.append_column(
        pa.field("changeset", type=pa.string(), nullable=True),
        [[changeset_id] * len(changeset)],
    ).append_column(
        pa.field("last_modified", type=pa.timestamp("us", "UTC"), nullable=True),
        [[timestamp] * len(changeset)],
    )
    return changeset


@timeit
def _find_updates(existing_data: pa.Table, new_data: pa.Table):
    return get_rows_to_update(new_data, existing_data,
                              ["namespace", "id"], ["content_hash"])


@timeit
def _find_inserts(existing_data: pa.Table, new_data: pa.Table, record_namespace: str):
    old_ids = existing_data.column("id")
    missing_records = new_data.filter(
        (pc.field("namespace") == record_namespace) & ~pc.field("id").isin(old_ids)
    )
    return missing_records


@timeit
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


def hash_content(content):
    return sha3_256(content.encode()).hexdigest()


def update_content_hash(table: pa.Table) -> pa.Table:
    # Initialize a list to store the computed hashes
    content_hashes = []

    # Get the column arrays
    content_column = table.column("content")

    # Iterate over the rows in the content column
    for content in content_column:
        content_hash = hash_content(content.as_py())
        content_hashes.append(content_hash)

    # Create a new array for the content_hash column
    content_hashes_array = pa.array(content_hashes)

    # Add the new column to the table
    updated_table = table.set_column(2, "content_hash", content_hashes_array)

    return updated_table


def get_rows_to_update(source_table: pa.Table, target_table: pa.Table, join_cols: list[str],
                       compare_cols: list[str]) -> pa.Table:
    """
    Return a table with rows that need to be updated in the target table based on the join columns.

    The table is joined on the identifier columns, and then checked if there are any updated rows.
    Those are selected and everything is renamed correctly.
    """
    all_columns = set(source_table.column_names)
    join_cols_set = set(join_cols)
    non_key_cols = all_columns - join_cols_set

    if len(target_table) == 0:
        # When the target table is empty, there is nothing to update :)
        return source_table.schema.empty_table()

    diff_expr = functools.reduce(operator.or_,
                                 [pc.field(f"{col}-lhs") != pc.field(f"{col}-rhs") for col in compare_cols])

    return (
        source_table
        # We already know that the schema is compatible, this is to fix large_ types
        .cast(target_table.schema)
        .join(target_table, keys=list(join_cols_set), join_type="inner", left_suffix="-lhs", right_suffix="-rhs")
        .filter(diff_expr)
        .drop_columns([f"{col}-rhs" for col in non_key_cols])
        .rename_columns({f"{col}-lhs" if col not in join_cols else col: col for col in source_table.column_names})
        # Finally cast to the original schema since it doesn't carry nullability:
        # https://github.com/apache/arrow/issues/45557
    ).cast(target_table.schema)
