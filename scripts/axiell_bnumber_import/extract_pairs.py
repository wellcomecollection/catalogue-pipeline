#!/usr/bin/env python3
"""Extract (CALM RecordID, b number) pairs from Sierra source works.

Reads the production pipeline's works-source index and emits one CSV row per
Sierra bib that carries a calm-record-id merge candidate (the bib's 035, i.e.
the exact link the merger uses today). See README.md; tracked in
wellcomecollection/platform#6525.

Run from the repo root with the catalogue_graph environment:

    AWS_PROFILE=platform-developer uv run --project catalogue_graph \
        python scripts/axiell_bnumber_import/extract_pairs.py
"""

import argparse
import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "catalogue_graph" / "src"))

from elasticsearch.helpers import scan
from utils.elasticsearch import get_client

SIERRA_FILTER = {
    "term": {"state.sourceIdentifier.identifierType.id": "sierra-system-number"}
}
SOURCE_FIELDS = ["state.sourceIdentifier.value", "state.mergeCandidates"]


def extract(es, index: str) -> tuple[set[tuple[str, str]], int, int]:
    pairs: set[tuple[str, str]] = set()
    scanned = 0
    with_candidate = 0
    for hit in scan(
        es,
        index=index,
        query={"query": {"bool": {"filter": [SIERRA_FILTER]}}},
        _source_includes=SOURCE_FIELDS,
        size=2000,
        preserve_order=False,
    ):
        scanned += 1
        state = hit["_source"]["state"]
        b_number = state["sourceIdentifier"]["value"]
        found = False
        for candidate in state.get("mergeCandidates", []):
            sid = candidate.get("id", {}).get("sourceIdentifier", {})
            if sid.get("identifierType", {}).get("id") == "calm-record-id":
                pairs.add((sid["value"], b_number))
                found = True
        if found:
            with_candidate += 1
        if scanned % 200_000 == 0:
            print(f"...{scanned} scanned, {len(pairs)} pairs", file=sys.stderr)
    return pairs, scanned, with_candidate


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pipeline-date", default="2025-10-02")
    parser.add_argument("--es-mode", default="public", choices=["public", "private"])
    parser.add_argument("--output", default="pairs.csv", type=Path)
    args = parser.parse_args()

    es = get_client("read_only", args.pipeline_date, args.es_mode)
    index = f"works-source-{args.pipeline_date}"
    pairs, scanned, with_candidate = extract(es, index)

    with args.output.open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["calm_record_id", "b_number"])
        writer.writerows(sorted(pairs))

    uuids = {u for u, _ in pairs}
    multi = len(pairs) - len(uuids)
    print(
        f"Scanned {scanned} Sierra source works in {index}: "
        f"{with_candidate} carry a calm-record-id merge candidate, "
        f"{len(pairs)} pairs over {len(uuids)} distinct RecordIDs "
        f"({multi} extra rows from RecordIDs with multiple bibs)."
    )
    print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
