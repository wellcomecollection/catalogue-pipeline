import sys
from common import setup_database
from pyiceberg.expressions import EqualTo


def main(changeset_id):
    catalogue, table = setup_database("mydb")
    print(f"  total records: {table.scan().count()}")
    print(
        f"changed records: {table.scan(row_filter=EqualTo('changeset', changeset_id)).count()}"
    )


if __name__ == "__main__":
    main(sys.argv[1])
