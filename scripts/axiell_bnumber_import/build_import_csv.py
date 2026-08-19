#!/usr/bin/env python3
"""Build the Axiell Collections b number import CSV from extracted pairs.

Joins pairs.csv (see extract_pairs.py) against the live Axiell adapter store,
keyed on the CALM RecordID each AxC record carries in MARC 907, and writes:

- the import CSV in the agreed format (object_number = the record's public
  reference, the AltRefNo; alternative_number = the b number;
  alternative_number.type = the constant "Bibliographic Number") for records
  matched in AxC that do not already cite the b number
- conflicts.csv where the AxC record cites a different b number that is
  still live in Sierra (with --bnumber-status, conflicts whose existing value
  is dead in Sierra are imported instead of withheld)
- ambiguous_refs.csv where several AxC records share the public reference the
  import would match on
- no_public_ref.csv where the matched record carries no AltRefNo to match on
- unmatched.csv for pairs with no AxC record (expected for manuscripts moving
  to TEI and returned PSY material)
- report.md with the counts

The Axiell import matches records on object_number, so the join here on the
CALM RecordID is what derives and verifies each record's public reference
before it is used as the match key. Tracked in wellcomecollection/platform#6525.

Run from the repo root with the catalogue_graph environment:

    AWS_PROFILE=platform-read_only uv run --project catalogue_graph \
        python scripts/axiell_bnumber_import/build_import_csv.py
"""

import argparse
import csv
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "catalogue_graph" / "src"))

from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.utils.iceberg import get_rest_api_table

from check_bnumbers import normalise

RE_245 = re.compile(
    r'tag="245"[^>]*>.*?<(?:marc:)?subfield code="a">([^<]*)</', re.DOTALL
)
RE_907 = re.compile(
    r'tag="907"[^>]*>.*?<(?:marc:)?subfield code="a">([^<]*)</', re.DOTALL
)
RE_035 = re.compile(
    r'tag="035"[^>]*>\s*<(?:marc:)?subfield code="a">\s*\(([^)]+)\)([^<]*)</', re.DOTALL
)
RE_UUID = re.compile(r"^[0-9a-fA-F-]{36}$")


def scan_axiell_store() -> dict[str, dict]:
    """Map each record's 907 CALM RecordID to its RefNo and existing b numbers."""
    table = get_rest_api_table(
        AXIELL_ADAPTER_CONFIG.rest_api_iceberg_config, create_if_not_exists=False
    )
    by_uuid: dict[str, dict] = {}
    scanned = 0
    # The store schema allows several sources per table; take only Axiell rows.
    reader = table.scan(
        row_filter=f"namespace = '{AXIELL_ADAPTER_CONFIG.adapter_namespace}'",
        selected_fields=("id", "content", "deleted"),
    ).to_arrow_batch_reader()
    for batch in reader:
        d = batch.to_pydict()
        for rid, content, deleted in zip(d["id"], d["content"], d["deleted"]):
            scanned += 1
            if deleted or not content:
                continue
            m907 = RE_907.search(content)
            if not m907:
                continue
            uuid = m907.group(1).strip().lower()
            if not RE_UUID.match(uuid):
                continue
            refno = ""
            altrefno = ""
            m245 = RE_245.search(content)
            title = m245.group(1).strip() if m245 else ""
            bnumbers = set()
            for prefix, value in RE_035.findall(content):
                if prefix == "Calm RefNo" and not refno:
                    refno = value.strip()
                elif prefix == "AltRefNo" and not altrefno:
                    altrefno = value.strip()
                elif prefix == "Bibliographic Number":
                    bnumbers.add(value.strip().lstrip("."))
            by_uuid[uuid] = {
                "record_id": rid,
                "refno": refno,
                "altrefno": altrefno,
                "title": title,
                "bnumbers": bnumbers,
            }
    print(
        f"Scanned {scanned} adapter store rows, {len(by_uuid)} with a 907 RecordID",
        file=sys.stderr,
    )
    return by_uuid


def write_csv(path: Path, header: list[str], rows: list[list[str]]) -> None:
    with path.open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(sorted(rows))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pairs", default="pairs.csv", type=Path)
    parser.add_argument("--output-dir", default=Path("."), type=Path)
    parser.add_argument(
        "--since",
        type=Path,
        help="A previous import CSV; only rows not already in it are emitted",
    )
    parser.add_argument(
        "--bnumber-status",
        type=Path,
        help="Output of check_bnumbers.py; lets dead-valued conflicts import",
    )
    parser.add_argument(
        "--include-live-conflicts",
        action="store_true",
        help="Also import our b number where the record cites a different live "
        "one (collections decision of 2026-08-19: import ours, the duplicate "
        "cataloguing is resolved on the Axiell side); conflicts.csv is still "
        "written for that follow-up",
    )
    args = parser.parse_args()

    with args.pairs.open() as f:
        pairs = [
            (r["calm_record_id"].lower(), r["b_number"]) for r in csv.DictReader(f)
        ]

    by_uuid = scan_axiell_store()

    status: dict[str, tuple[str, str, str]] = {}
    if args.bnumber_status:
        with args.bnumber_status.open() as f:
            for r in csv.DictReader(f):
                status[r["bnumber"]] = (
                    r["status"],
                    r["sierra_format"],
                    r["sierra_title"],
                )

    ref_owners: dict[str, set[str]] = {}
    for uuid, record in by_uuid.items():
        if record["altrefno"]:
            ref_owners.setdefault(record["altrefno"], set()).add(uuid)

    to_import: list[list[str]] = []
    already: list[list[str]] = []
    conflicts: list[list[str]] = []
    ambiguous: list[list[str]] = []
    no_public_ref: list[list[str]] = []
    unmatched: list[list[str]] = []
    for uuid, b_number in pairs:
        record = by_uuid.get(uuid)
        if record is None:
            unmatched.append([uuid, b_number])
        elif b_number in record["bnumbers"]:
            already.append([uuid, b_number])
        elif record["bnumbers"]:
            resolved = [
                status.get(normalise(b), ("unknown", "", ""))
                for b in sorted(record["bnumbers"])
            ]
            if args.include_live_conflicts or (
                status
                and all(s in ("deleted", "absent", "malformed") for s, _, _ in resolved)
            ):
                # Nothing usable is lost whether the import appends or replaces.
                to_import.append([record["altrefno"], b_number, "Bibliographic Number"])
            if not all(s in ("deleted", "absent", "malformed") for s, _, _ in resolved):
                conflicts.append(
                    [
                        record["altrefno"],
                        record["refno"],
                        record["title"],
                        b_number,
                        ";".join(sorted(record["bnumbers"])),
                        ";".join(s for s, _, _ in resolved),
                        ";".join(f for _, f, _ in resolved),
                        ";".join(ti for _, _, ti in resolved),
                        uuid,
                    ]
                )
        elif not record["altrefno"]:
            no_public_ref.append([uuid, b_number, record["refno"]])
        elif len(ref_owners[record["altrefno"]]) > 1:
            ambiguous.append([record["altrefno"], uuid, b_number])
        else:
            to_import.append([record["altrefno"], b_number, "Bibliographic Number"])

    if args.since:
        with args.since.open() as f:
            previous = {
                (r["object_number"], r["alternative_number"]) for r in csv.DictReader(f)
            }
        to_import = [row for row in to_import if (row[0], row[1]) not in previous]

    out = args.output_dir
    out.mkdir(parents=True, exist_ok=True)
    write_csv(
        out / "axiell_bnumber_import.csv",
        ["object_number", "alternative_number", "alternative_number.type"],
        to_import,
    )
    write_csv(
        out / "conflicts.csv",
        [
            "AltRefNo",
            "RefNo",
            "axc_title",
            "our_bnumber",
            "axc_bnumbers",
            "axc_bnumber_status",
            "axc_bnumber_sierra_format",
            "axc_bnumber_sierra_title",
            "RecordID",
        ],
        conflicts,
    )
    write_csv(
        out / "ambiguous_refs.csv", ["AltRefNo", "RecordID", "Bnumber"], ambiguous
    )
    write_csv(
        out / "no_public_ref.csv", ["RecordID", "Bnumber", "RefNo"], no_public_ref
    )
    write_csv(out / "unmatched.csv", ["RecordID", "Bnumber"], unmatched)

    report = "\n".join(
        [
            "# Axiell b number import build",
            "",
            f"- pairs read: {len(pairs)}",
            f"- to import: {len(to_import)}"
            + (" (delta since previous CSV)" if args.since else ""),
            f"- already present in AxC: {len(already)}",
            f"- conflicts (AxC cites a different b number): {len(conflicts)}",
            f"- public ref (AltRefNo) shared by several AxC records: {len(ambiguous)}",
            f"- AxC record has no AltRefNo to match on: {len(no_public_ref)}",
            f"- no AxC record for the RecordID: {len(unmatched)}",
            "",
        ]
    )
    (out / "report.md").write_text(report)
    print(report)


if __name__ == "__main__":
    main()
