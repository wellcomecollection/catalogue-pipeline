#!/usr/bin/env python3
"""Resolve every 035 (Bibliographic Number) in the Axiell store against Sierra.

Writes bnumber_status.csv with one row per (record, b number): the value as
found, its normalised form, and its status in Sierra (live, deleted, or
absent), with the Sierra record's format and title where it exists. Feed the
output to build_import_csv.py --bnumber-status so conflicts whose existing
value is dead are imported rather than withheld.

Run from the repo root with the catalogue_graph environment; needs
platform-developer for the pipeline ES secret:

    AWS_PROFILE=platform-developer uv run --project catalogue_graph \
        python scripts/axiell_bnumber_import/check_bnumbers.py
"""

import argparse
import csv
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "catalogue_graph" / "src"))

from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.utils.iceberg import get_rest_api_table
from utils.elasticsearch import get_client

RE_035 = re.compile(
    r'tag="035"[^>]*>\s*<(?:marc:)?subfield code="a">\s*\(([^)]+)\)([^<]*)</', re.DOTALL
)
RE_B = re.compile(r"^b[0-9]{7}[0-9x]$")


def normalise(value: str) -> str:
    return value.strip().lstrip(".").lower().replace(" ", "")


def scan_store() -> list[list[str]]:
    table = get_rest_api_table(
        AXIELL_ADAPTER_CONFIG.rest_api_iceberg_config, create_if_not_exists=False
    )
    rows = []
    reader = table.scan(
        row_filter=f"namespace = '{AXIELL_ADAPTER_CONFIG.adapter_namespace}'",
        selected_fields=("id", "content", "deleted"),
    ).to_arrow_batch_reader()
    for batch in reader:
        d = batch.to_pydict()
        for rid, content, deleted in zip(d["id"], d["content"], d["deleted"]):
            if deleted or not content:
                continue
            ref = ""
            for prefix, value in RE_035.findall(content):
                if prefix == "AltRefNo" and not ref:
                    ref = value.strip()
            for prefix, value in RE_035.findall(content):
                if prefix == "Bibliographic Number":
                    rows.append([rid, ref, value.strip(), normalise(value)])
    return rows


def lookup_sierra(pipeline_date: str, es_mode: str, values: set[str]) -> dict:
    es = get_client("read_only", pipeline_date, es_mode)
    index = f"works-source-{pipeline_date}"
    found = {}
    vals = sorted(values)
    for i in range(0, len(vals), 500):
        resp = es.search(
            index=index,
            size=500,
            query={
                "bool": {
                    "filter": [
                        {"terms": {"state.sourceIdentifier.value": vals[i : i + 500]}},
                        {
                            "term": {
                                "state.sourceIdentifier.identifierType.id": "sierra-system-number"
                            }
                        },
                    ]
                }
            },
            _source=[
                "state.sourceIdentifier.value",
                "type",
                "data.format.label",
                "data.title",
            ],
        )
        for hit in resp["hits"]["hits"]:
            source = hit["_source"]
            found[source["state"]["sourceIdentifier"]["value"]] = (
                "deleted" if source.get("type") == "Deleted" else "live",
                source.get("data", {}).get("format", {}).get("label", ""),
                (source.get("data", {}).get("title") or "")[:80],
            )
    return found


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pipeline-date", default="2025-10-02")
    parser.add_argument("--es-mode", default="public", choices=["public", "private"])
    parser.add_argument("--output", default="bnumber_status.csv", type=Path)
    args = parser.parse_args()

    rows = scan_store()
    wellformed = {r[3] for r in rows if RE_B.match(r[3])}
    found = lookup_sierra(args.pipeline_date, args.es_mode, wellformed)

    out = []
    for rid, ref, raw, norm in rows:
        if not RE_B.match(norm):
            status, fmt, title = "malformed", "", ""
        else:
            status, fmt, title = found.get(norm, ("absent", "", ""))
        out.append([rid, ref, raw, norm, status, fmt, title])

    with args.output.open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(
            [
                "axc_id",
                "ref",
                "raw_bnumber",
                "bnumber",
                "status",
                "sierra_format",
                "sierra_title",
            ]
        )
        writer.writerows(sorted(out))

    from collections import Counter

    print(f"{len(out)} rows written to {args.output}")
    print("status counts:", dict(Counter(r[4] for r in out)))


if __name__ == "__main__":
    main()
