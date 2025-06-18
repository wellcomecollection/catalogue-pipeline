import os
from pyiceberg.catalog import load_catalog
from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, IntegerType, StringType

from pyiceberg.table.upsert_util import get_rows_to_update

import pyarrow as pa
import pyarrow.compute as pc
import uuid

SCHEMA = Schema(
    NestedField(field_id=1, name='id', field_type=StringType(), required=True),
    NestedField(field_id=3, name='content', field_type=StringType(), required=False),
    NestedField(field_id=2, name='changeset', field_type=StringType(), required=False)
)


def main():
    table = setup_database("main")
    arrow_schema = pa.schema(
        [pa.field("id", type=pa.string(), nullable=False), pa.field("content", type=pa.string(), nullable=True)])
    initial_data = pa.Table.from_pylist([
        {'id': "eb0001", 'content': 'metric_1'},
        {'id': "eb0002", 'content': 'metric_2'},
        {'id': "eb0003", 'content': 'metric_3'},
        {'id': "eb0099", 'content': 'metric_99'}
    ],
        schema=arrow_schema)

    # table.append(initial_data)
    initial_data = table.scan(selected_fields=["id", "content"]).to_arrow().cast(arrow_schema)
    new_data = pa.Table.from_pylist([
        {'id': "eb0099", 'content': 'metric_099'},
        {'id': "eb0001", 'content': 'metric_0'},
        {'id': "eb0003", 'content': 'metric_3'},
        {'id': "eb0004", 'content': 'metric_4'}
    ],
        schema=arrow_schema)
    changeset_id = str(uuid.uuid1())
    new_ids = set(new_data.column("id"))
    old_ids = set(initial_data.filter(~ pc.field('content').is_null()).column("id"))
    deleted_ids = old_ids - new_ids
    deletes = pa.Table.from_pylist([
        {'id': id.as_py()}
        for id in deleted_ids
    ], schema=arrow_schema)
    inserted_ids = new_ids - old_ids
    rows = get_rows_to_update(new_data, initial_data, ["id"])
    changed_ids = set(rows.column('id'))
    upsert_ids = changed_ids | inserted_ids
    if upsert_ids:
        changed_fields = new_data.filter(pc.field("id").isin(upsert_ids))
    else:
        changed_fields = pa.Table.from_pylist([], schema=arrow_schema)
    changeset = pa.concat_tables([changed_fields, deletes])
    if (changeset):
        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            [[changeset_id] * (len(upsert_ids) + len(deleted_ids))]
        )
        ubs = table.upsert(changeset, ['id'])
        print(ubs)
    else:
        print("nothing to do")
    print(table.scan().to_arrow())


"""
"""


def setup_database(table_name):
    catalog = load_catalog("local", uri="sqlite:////tmp/warehouse/catalog.db", warehouse="file:///tmp/warehouse/")
    print(catalog.properties)
    catalog.create_namespace_if_not_exists("default")
    table = catalog.create_table_if_not_exists(
        identifier=f'default.{table_name}',
        schema=SCHEMA
    )
    return table


if __name__ == "__main__":
    main()
