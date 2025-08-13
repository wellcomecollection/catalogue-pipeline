import sys

from pyiceberg.expressions import EqualTo, IsNull

from table_config import get_local_table

# This is just a quick starting point for the next work, allowing us to quickly examine that
# changes have been made, and that pre-existing data is left alone


def main(changeset_id: str) -> None:
    table = get_local_table()
    print(f"  total records: {table.scan().count()}")
    print(
        f"changed records: {table.scan(row_filter=EqualTo('changeset', changeset_id)).count()}"
    )
    print(
        f"deleted records: {table.scan(row_filter=EqualTo('changeset', changeset_id) & IsNull('content')).count()}"
    )
    print(
        f"sample records: {table.scan(row_filter=EqualTo('changeset', changeset_id), limit=2).to_arrow()}"
    )
    #


if __name__ == "__main__":
    main(sys.argv[1])
