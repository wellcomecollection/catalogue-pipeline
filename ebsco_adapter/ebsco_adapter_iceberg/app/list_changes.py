import sys
from common import setup_database
from pyiceberg.expressions import EqualTo


def main(changeset_id):
    catalogue, table = setup_database("mydb")
    print(table.scan().count())
    print(len(table.scan(
        row_filter=EqualTo("changeset", changeset_id), selected_fields=["id"]
    ).to_arrow()))


if __name__ == "__main__":
    main(sys.argv[1])
