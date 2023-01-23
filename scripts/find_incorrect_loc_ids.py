"""
Look through a list of Sierra JSON files as downloaded from s3, and finds any
identifiers that claim to come from Library of Congress, but don't.
"""

import click
import json
import re
import csv


@click.command()
@click.argument("files", type=click.File("rb"), nargs=-1)
def main(files):
    with open("dodgy-ids.csv", "w") as csvfile:
        dodgywriter = csv.writer(csvfile, quoting=csv.QUOTE_MINIMAL)
        dodgywriter.writerow(("sierra number", "b number", "non-compliant identifiers"))
        for row in (find_dodgy_ids_in_file(filepath) for filepath in files):
            if row[2]:
                dodgywriter.writerow(row)


def find_dodgy_ids_in_file(filepath):
    return find_dodgy_ids_in_json(json.load(filepath))


def find_dodgy_ids_in_json(file_as_json):
    body_as_json = json.loads(file_as_json["maybeBibRecord"]["data"])
    return (
        file_as_json["sierraId"],
        find_b_number(body_as_json),
        find_dodgy_ids_in_object(body_as_json),
    )


def find_dodgy_ids_in_object(obj):
    return list(
        filter(
            is_not_loc_id,
            [
                id_from_varfield(varfield)
                for varfield in obj["varFields"]
                if should_be_loc_id(varfield)
            ],
        )
    )


def find_b_number(obj):
    for varfield in obj["varFields"]:
        if varfield.get("marcTag") == "907":
            return varfield["subfields"][0]["content"]


def should_be_loc_id(varfield):
    return (
        varfield.get("marcTag")
        in ["648", "650", "651", "655", "100", "110", "700", "710"]
        and varfield["ind2"] == "0"
    )


def is_not_loc_id(id_pair):
    if id_pair[1] is None:
        return False
    prefix = re.split("[\d|\s]", id_pair[1])[0]

    return prefix != "" and prefix[0] != "n" and prefix != "sh"


def id_from_varfield(varfield):
    title = None
    identifier = None
    for subfield in varfield["subfields"]:
        if subfield["tag"] == "a":
            title = subfield["content"]
        if subfield["tag"] == "0":
            identifier = subfield["content"]
    return title, identifier


if __name__ == "__main__":
    main()
