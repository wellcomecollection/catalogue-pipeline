from pyiceberg.catalog import load_catalog
from schemata import SCHEMA, ARROW_SCHEMA


def setup_database(table_name):
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
    #    table.append(initial_data)

    return catalog, table
