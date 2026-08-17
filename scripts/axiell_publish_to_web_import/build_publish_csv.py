#!/usr/bin/env python3
"""Build the Axiell Collections publish_to_web import CSV.

The round 2 migration left the publish_to_web (wp) checkbox empty on every
record in collect. Per the agreed mapping (Calm2Collections workbook, sheet
"Archive catalogue", CatalogueStatus row) the checkbox should be ticked for
records whose CALM CatalogueStatus is Catalogued, Partially catalogued or Not
yet available, and left unticked otherwise; in particular "Closed description"
records are catalogued but must not go online, and in AxC they are
indistinguishable from ordinary catalogued records, so the tick list is
derived from the CALM-side status (see extract_calm_statuses.py).

Joins calm_statuses.csv against the Axiell adapter store (live REST table, or
a rebuild snapshot parquet via --snapshot-path), keyed on the CALM RecordID in
MARC 907, and writes:

- axiell_publish_to_web_import.csv: object_number (the AltRefNo the import
  matches on) plus the publish_to_web tick value, for in-load records whose
  CALM status ticks
- sample.csv: the first 10 rows, for a trial import
- withheld.csv: tickable records withheld because they have no AltRefNo or
  share one with another record
- excluded_suppressed.csv: in-load records whose CALM status does NOT tick
  (closed description, for deletion, and so on), for review
- report.md with the counts

Run from the repo root with the catalogue_graph environment:

    AWS_PROFILE=platform-read_only uv run --project catalogue_graph \
        python scripts/axiell_publish_to_web_import/build_publish_csv.py
"""

import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "catalogue_graph" / "src"))

TICK_STATUSES = {"catalogued", "partially catalogued", "not yet available"}

RE_907 = re.compile(
    r'tag="907"[^>]*>.*?<(?:marc:)?subfield code="a">([^<]*)</', re.DOTALL
)
RE_035_ALTREF = re.compile(
    r'tag="035"[^>]*>\s*<(?:marc:)?subfield code="a">\s*\(AltRefNo\)([^<]*)</',
    re.DOTALL,
)
RE_UUID = re.compile(r"^[0-9a-fA-F-]{36}$")


def iter_store_records(snapshot_path: str | None):
    """Yield (axc_id, content) for live records, from a snapshot or the store."""
    if snapshot_path:
        import pyarrow.parquet as pq

        reader = pq.ParquetFile(snapshot_path).iter_batches(batch_size=2000)
    else:
        from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
        from adapters.utils.iceberg import get_rest_api_table

        table = get_rest_api_table(
            AXIELL_ADAPTER_CONFIG.rest_api_iceberg_config, create_if_not_exists=False
        )
        reader = table.scan(
            selected_fields=("id", "content", "deleted")
        ).to_arrow_batch_reader()

    for batch in reader:
        d = batch.to_pydict()
        deleted = d.get("deleted", [False] * len(d["id"]))
        for axc_id, content, dead in zip(d["id"], d["content"], deleted):
            if dead or not content:
                continue
            yield axc_id, content


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--calm-statuses",
        default=str(Path(__file__).parent / "calm_statuses.csv"),
        help="CSV from extract_calm_statuses.py",
    )
    parser.add_argument(
        "--snapshot-path",
        help="Rebuild snapshot parquet to read instead of the live store",
    )
    parser.add_argument(
        "--tick-value",
        default="x",
        help="Value written to the publish_to_web column (default: x)",
    )
    parser.add_argument(
        "--out-dir",
        default=str(Path(__file__).parent),
        help="Directory for the output files",
    )
    args = parser.parse_args()
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    calm_status: dict[str, str] = {}
    with open(args.calm_statuses, newline="") as f:
        for row in csv.DictReader(f):
            calm_status[row["record_id"].lower()] = (
                row["catalogue_status"].strip().lower()
            )
    print(f"CALM statuses loaded: {len(calm_status)}", flush=True)

    ticks: list[tuple[str, str]] = []  # (alt_ref_no, axc_id)
    no_altref: list[tuple[str, str]] = []  # (axc_id, calm_status)
    excluded: list[tuple[str, str, str]] = []  # (axc_id, alt_ref_no, calm_status)
    tally: Counter = Counter()

    for axc_id, content in iter_store_records(args.snapshot_path):
        tally["in store"] += 1
        m907 = RE_907.search(content)
        uuid = m907.group(1).strip().lower() if m907 else ""
        status = calm_status.get(uuid) if RE_UUID.match(uuid) else None
        m_altref = RE_035_ALTREF.search(content)
        altref = m_altref.group(1).strip() if m_altref else ""

        if status is None:
            tally["no CALM link (left unticked)"] += 1
        elif status in TICK_STATUSES:
            if altref:
                ticks.append((altref, axc_id))
                tally[f"tick: {status}"] += 1
            else:
                no_altref.append((axc_id, status))
                tally["WITHHELD: tickable but no AltRefNo"] += 1
        else:
            excluded.append((axc_id, altref, status))
            tally[f"excluded: {status}"] += 1

    # The import matches on object_number, so a shared AltRefNo is ambiguous.
    ref_counts = Counter(ref for ref, _ in ticks)
    shared = {ref for ref, n in ref_counts.items() if n > 1}
    importable = [(ref, axc_id) for ref, axc_id in ticks if ref not in shared]
    withheld_shared = [(ref, axc_id) for ref, axc_id in ticks if ref in shared]
    importable.sort()

    import_path = out_dir / "axiell_publish_to_web_import.csv"
    with open(import_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["object_number", "publish_to_web"])
        for ref, _ in importable:
            writer.writerow([ref, args.tick_value])

    with open(out_dir / "sample.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["object_number", "publish_to_web"])
        for ref, _ in importable[:10]:
            writer.writerow([ref, args.tick_value])

    with open(out_dir / "withheld.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["reason", "axc_id", "alt_ref_no", "calm_status"])
        for axc_id, status in no_altref:
            writer.writerow(["no AltRefNo", axc_id, "", status])
        for ref, axc_id in withheld_shared:
            writer.writerow(["shared AltRefNo", axc_id, ref, ""])

    with open(out_dir / "excluded_suppressed.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["axc_id", "alt_ref_no", "calm_status"])
        writer.writerows(sorted(excluded, key=lambda r: (r[2], r[0])))

    with open(out_dir / "report.md", "w") as f:
        f.write("# publish_to_web import build report\n\n")
        f.write(f"Importable rows: {len(importable)}\n\n")
        for key, count in sorted(tally.items()):
            f.write(f"- {key}: {count}\n")
        f.write(f"- WITHHELD: shared AltRefNo: {len(withheld_shared)}\n")

    print(f"importable: {len(importable)} -> {import_path}", flush=True)
    for key, count in sorted(tally.items()):
        print(f"  {key}: {count}", flush=True)
    print(f"  WITHHELD: shared AltRefNo: {len(withheld_shared)}", flush=True)


if __name__ == "__main__":
    main()
