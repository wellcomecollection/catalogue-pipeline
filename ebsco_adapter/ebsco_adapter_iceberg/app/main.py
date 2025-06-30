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
import xml.etree.ElementTree as ET

import sys


def update_from_xml_file(table: IcebergTable, xmlfile):
    return update_from_xml(table, ET.parse(xmlfile).getroot())


def update_from_xml(table: IcebergTable, collection: ET.ElementTree):
    for record in collection:
        ebsco_id = record.find(
            "{http://www.loc.gov/MARC21/slim}controlfield[@tag='001']"
        ).text
        print(ebsco_id)


def update_table(table: IcebergTable, new_data: pa.Table):
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

           (
            TODO: should this function also return the latest snapshot ID to pass on?
                pro: That would allow us to accurately pass the changeset downstream, even if another change arrives shortly after
                cont: I don't think that is desirable, as the next change will overwrite it anyway
           )
    """

    # Pull out a table view excluding the existing changeset column, and any deleted rows.
    # the incoming data does not know about changesets, so this allows us to perform a table comparison
    # based on the "real" data.
    # records that have already been "deleted" do not need to be deleted again.

    existing_data = (
        table.scan(row_filter=Not(IsNull("content")), selected_fields=["id", "content"])
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
        deletes = get_deletes(existing_data, new_data)
        # pyiceberg offers a built in feature that returns the update rows
        # by comparing two tables.  Unfortunately, it does not say anything
        # about inserts.
        updates = find_updates(existing_data, new_data)
        inserts = find_inserts(existing_data, new_data)
        changeset = pa.concat_tables([deletes, updates, inserts])
    else:
        # Empty DB short-circuit, just write it all in.
        changeset = new_data
    if changeset:
        changeset_id = str(uuid.uuid1())  # maybe just use a timestamp?
        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            [[changeset_id] * len(changeset)],
        )
        table.upsert(changeset, ["id"])
        return changeset_id
    else:
        print("nothing to do")
        return None


def find_updates(existing_data: pa.Table, new_data: pa.Table):
    return get_rows_to_update(new_data, existing_data, ["id"])


def find_inserts(existing_data: pa.Table, new_data: pa.Table):
    old_ids = existing_data.column("id")
    missing_records = new_data.filter(~pc.field("id").isin(old_ids))
    return missing_records


def get_deletes(existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
    """
    Find records in `existing_data` that are not in `new_data`, and produce a
    pyarrow Table that can be used to update those records by emptying their content.
    """
    new_ids = new_data.column("id")
    missing_ids = existing_data.filter(~pc.field("id").isin(new_ids)).column("id")
    return pa.Table.from_pylist(
        [{"id": id.as_py()} for id in missing_ids], schema=ARROW_SCHEMA
    )


def data_to_pa_table(data):
    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def main(xmlfile):
    """Do Something"""
    update_from_xml_file(None, xmlfile)


def setup_database(table_name, initial_data):
    catalog = load_catalog(
        "local",
        uri="sqlite:////tmp/warehouse/catalog.db",
        warehouse="file:///tmp/warehouse/",
    )
    table_fullname = f"default.{table_name}"
    catalog.create_namespace_if_not_exists("default")

    if catalog.table_exists(table_fullname):
        table = catalog.load_table(table_fullname)
    else:
        table = catalog.create_table_if_not_exists(
            identifier=table_fullname, schema=SCHEMA
        )
        table.append(initial_data)

    return catalog, table


if __name__ == "__main__":
    main(sys.stdin)
