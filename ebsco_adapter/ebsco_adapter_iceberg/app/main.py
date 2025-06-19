import os
from pyiceberg.catalog import load_catalog
from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, IntegerType, StringType
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

import pyarrow as pa
import pyarrow.compute as pc
import uuid

from pyiceberg.expressions import Not, IsNull
from schemata import SCHEMA, ARROW_SCHEMA


def update_table(table: IcebergTable, new_data: pa.Table):
    """
    Perform an update on `table`, using the data provided in new_data.

    This is an enhanced upsert that also:
    - "deletes" records that are absent in new_data
        - deletion involves blanking the content of a row, but leaving the id intact.
            this allows us to push the identifier of the deleted record downstream
            even if the workflow run that performs the deletion fails.
    - adds an explicit changeset identifier for all records altered in this update.
        - a normal upsert consists of up to four separate operations, this allows us to easily group
            those changes together at a later stage.
    """
    # Pull out a table view excluding the existing changeset column, and any deleted rows.
    # the incoming data does not know about changesets, so this allows us to perform a table comparison
    # based on the "real" data.
    # records that have already been "deleted" do not need to be deleted again.

    existing_data = table.scan(
        row_filter=Not(IsNull('content')),
        selected_fields=["id", "content"]
    ).to_arrow().cast(ARROW_SCHEMA)
    if existing_data:
        deletes = get_deletes(existing_data, new_data)
        updates = find_updates(existing_data, new_data)
        inserts = find_inserts(existing_data, new_data)
        changeset = pa.concat_tables([deletes, updates, inserts])
    else:
        changeset = new_data
    if changeset:
        changeset_id = str(uuid.uuid1())  # maybe just use a timestamp?
        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            [[changeset_id] * len(changeset)]
        )
        table.upsert(changeset, ['id'])
        return changeset_id
    else:
        print("nothing to do")
        return None


def find_updates(existing_data: pa.Table, new_data: pa.Table):
    return get_rows_to_update(new_data, existing_data, ["id"])


def find_inserts(existing_data: pa.Table, new_data: pa.Table):
    old_ids = existing_data.column("id")
    missing_records = new_data.filter(~ pc.field("id").isin(old_ids))
    return missing_records


def get_deletes(existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
    """
    Find records in `existing_data` that are not in `new_data`, and produce a
    pyarrow Table that can be used to update those records by emptying their content.
    """
    new_ids = new_data.column("id")
    missing_ids = existing_data.filter(~ pc.field("id").isin(new_ids)).column("id")
    return pa.Table.from_pylist([
        {'id': id.as_py()}
        for id in missing_ids
    ], schema=ARROW_SCHEMA)


def data_to_pa_table(data):
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def main():
    """Do Something"""


def setup_database(table_name, initial_data):
    catalog = load_catalog("local", uri="sqlite:////tmp/warehouse/catalog.db", warehouse="file:///tmp/warehouse/")
    table_fullname = f'default.{table_name}'
    catalog.create_namespace_if_not_exists("default")

    if (catalog.table_exists(table_fullname)):
        table = catalog.load_table(table_fullname)
    else:
        table = catalog.create_table_if_not_exists(
            identifier=table_fullname,
            schema=SCHEMA
        )
        table.append(initial_data)

    return catalog, table


if __name__ == "__main__":
    main()
