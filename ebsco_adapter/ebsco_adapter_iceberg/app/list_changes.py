import sys
from iceberg_updates import get_table
from pyiceberg.expressions import EqualTo, IsNull


# This is just a quick starting point for the next work, allowing us to quickly examine that
# changes have been made, and that pre-existing data is left alone


def main(changeset_id):
    table = get_table(
        catalogue_name="local",
        catalogue_uri="sqlite:////tmp/warehouse/catalog.db",
        catalogue_warehouse="file:///tmp/warehouse/",
        catalogue_namespace="default",
        table_name="mytable",
    )
    print(f"  total records: {table.scan().count()}")
    print(
        f"changed records: {table.scan(row_filter=EqualTo('changeset', changeset_id)).count()}"
    )
    print(
        f"deleted records: {table.scan(row_filter=EqualTo('changeset', changeset_id) & IsNull('content')).count()}"
    )


if __name__ == "__main__":
    main(sys.argv[1])
