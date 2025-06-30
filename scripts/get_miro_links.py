#!/usr/bin/env python3
"""
This script is used to find links between Miro images that have been
individually re-catalogued in Calm and their source Sierra work.

The works from Calm contain a related material note that links to the
work page for the Sierra work. This script looks up the associated B
number for that work and creates a CSV file that contains work IDs,
Calm AltRefNos and Sierra B nUmbers for all re-catalogued images.

The output CSV file is then loaded manually into Sierra to create
notes with reciprocal links on the Sierra bibs.
"""

import csv
import re
import requests
from tqdm import tqdm

works = []
base_url = "https://api.wellcomecollection.org/catalogue/v2/works?partOf=qzcbm8q3&pageSize=100&include=notes"


def get_works(url):
    r = requests.get(url)
    data = r.json()
    results = data["results"]
    for work in tqdm(results, desc="Processing works"):
        if work["type"] == "Work":
            for note in work["notes"]:
                if note["noteType"]["id"] == "related-material":
                    related_works = process_note(note["contents"])
                    if len(related_works) > 0:
                        for related_work in related_works:
                            bnumbers = get_bnumbers(related_work)
                            for bnumber in bnumbers:
                                works.append(
                                    [
                                        work["id"],
                                        work["referenceNumber"],
                                        related_work,
                                        bnumber,
                                    ]
                                )
                    break
    if "nextPage" in data:
        get_works(data["nextPage"])


def process_note(contents):
    related_works = []
    for entry in contents:
        matches = re.findall(
            r'https://wellcomecollection.org/works/([^\'"/]+)[\'"]', entry
        )
        for match in matches:
            related_works.append(match)
    return related_works


def get_bnumbers(id):
    bnumbers = []
    r = requests.get(
        "https://api.wellcomecollection.org/catalogue/v2/works/%s?include=identifiers"
        % id
    )
    data = r.json()
    if "identifiers" in data:
        for identifier in data["identifiers"]:
            if identifier["identifierType"]["id"] == "sierra-system-number":
                bnumbers.append(identifier["value"])
    return bnumbers


def main():
    print("Fetching works and generating links...")
    get_works(base_url)
    print(f"Writing {len(works)} rows to mirolinks.csv...")
    with open("mirolinks.csv", "w") as f:
        writer = csv.writer(f)
        writer.writerow(
            ["Calm Work ID", "Calm AltRefNo", "Sierra Work ID", "Sierra B Number"]
        )
        writer.writerows(works)


main()
