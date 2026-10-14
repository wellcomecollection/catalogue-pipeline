#!/usr/bin/env python3
"""Print a harness event from the named target and mode.

    ./make_event.py write_matrix --target serverless_public > /tmp/wm.json
    ./make_event.py ping --target serverless_private
    ./make_event.py scroll --target hosted_public --index works-denormalised-2026-09-30
    ./make_event.py load --target serverless_public --slice-id 0 --slice-max 8

Source documents always come from the hosted 2026-09-30 indices through the
hosted bench key, so every target writes the same documents.
"""

import argparse
import json
from pathlib import Path

PIPELINE_DATE = "2026-10-14-serverless"
TARGETS = json.loads((Path(__file__).parent / "targets.json").read_text())

SAMPLE_INDEX = "works-denormalised-2026-09-30"
DEST_INDEX = {
    "serverless_public": f"works-denormalised-{PIPELINE_DATE}",
    "serverless_private": f"works-denormalised-{PIPELINE_DATE}",
    "hosted_public": f"works-denormalised-{PIPELINE_DATE}-hosted-comparator",
    "hosted_private": f"works-denormalised-{PIPELINE_DATE}-hosted-comparator",
}

BULK_SIZES = [1, 10, 50, 100]
CONCURRENCIES = [1, 5, 10, 30, 60]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["write_matrix", "merger_shape", "ping", "scroll", "load"])
    parser.add_argument("--target", required=True, choices=sorted(TARGETS))
    parser.add_argument("--source", default="hosted_public", choices=sorted(TARGETS))
    parser.add_argument("--index", default=None)
    parser.add_argument("--docs-per-cell", type=int, default=5000)
    parser.add_argument("--sample-count", type=int, default=5000)
    parser.add_argument("--slice-id", type=int, default=0)
    parser.add_argument("--slice-max", type=int, default=8)
    parser.add_argument("--version", type=int, default=None)
    args = parser.parse_args()

    event: dict = {"target": TARGETS[args.target]}
    dest = args.index or DEST_INDEX[args.target]

    if args.mode in ("write_matrix", "merger_shape"):
        cells = (
            [{"bulk_size": 1, "concurrency": 50}]
            if args.mode == "merger_shape"
            else [{"bulk_size": b, "concurrency": c} for b in BULK_SIZES for c in CONCURRENCIES]
        )
        event.update(
            mode="write_matrix",
            source=TARGETS[args.source],
            sample={"index": SAMPLE_INDEX, "count": args.sample_count, "slices": 4},
            write={
                "index": dest,
                "cells": cells,
                "docs_per_cell": args.docs_per_cell,
                "id_prefix": args.mode,
                "version": args.version,
            },
        )
    elif args.mode == "ping":
        event.update(mode="ping", ping={"requests": 50, "index": dest})
    elif args.mode == "scroll":
        event.update(mode="scroll", scroll={"index": dest, "keep_alive": "30m"})
    else:
        event.update(
            mode="load",
            source=TARGETS[args.source],
            load={
                "source_index": SAMPLE_INDEX,
                "dest_index": dest,
                "slice_id": args.slice_id,
                "slice_max": args.slice_max,
            },
            write={"index": dest, "cells": [], "version": args.version or 1},
        )
    print(json.dumps(event, indent=2))


if __name__ == "__main__":
    main()
