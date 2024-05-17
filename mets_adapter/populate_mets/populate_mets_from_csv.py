import csv
import sys
from populate_mets import specific


# Push each Born Digital bag mentioned in a CSV through the adapter.
# The CSV should have a row corresponding to each Bag (Item) thus:
# Item, MY/BAG/1/2/3
# Item, MY/BAG/4/5/6
# More columns can be added, and rows starting with other values are ignored.


def main(csv_path):
    with open(csv_path, "r") as csv_file:
        specific(extract_bag_ids(csv.reader(csv_file)))


def extract_bag_ids(csv_reader):
    for row in csv_reader:
        if row[0] == "Item":
            yield f"born-digital/{row[1]}"


if __name__ == "__main__":
    main(sys.argv[1])
