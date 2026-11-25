"""Find what differs between production and the FOLIO pipeline after merging.

Compares works-denormalised, then removes the differences that already exist
at works-identified. Steps, from scripts/es_index_comparison:

    uv run es-index-compare run-all --config configs/6586_folio_prod_denormalised.yaml
    uv run python merged_differences_6586.py ids
    uv run es-index-compare run-all --config configs/6586_folio_prod_identified.yaml
    uv run python merged_differences_6586.py subtract

`ids` writes the ids of the works that differ at works-denormalised.
`subtract` writes the remaining differences to
data/6586_merged_differences.jsonl and prints the number of works per field.
"""

import json
import re
import sys
from collections import Counter
from pathlib import Path

DATA = Path(__file__).parent / "data"
DENORMALISED = DATA / "6586-folio-prod-denormalised" / "diffs" / "diffs.jsonl"
IDENTIFIED = DATA / "6586-folio-prod-identified" / "diffs" / "diffs.jsonl"
IDS = DATA / "6586_merged_differing_ids.txt"
OUT = DATA / "6586_merged_differences.jsonl"


def read(path: Path):
    with path.open() as f:
        for line in f:
            yield json.loads(line)


def key(diff: dict) -> tuple:
    """Identify a difference without its position in a list."""
    path = re.sub(r"\[\d+\]", "[]", diff["path"])
    left = json.dumps(diff.get("left"), sort_keys=True)
    right = json.dumps(diff.get("right"), sort_keys=True)
    return path, diff["type"], left, right


def field(diff: dict) -> str:
    path = re.sub(r"\[\d+\]", "", diff["path"])
    return ".".join(path.split(".")[:2])


def write_ids() -> None:
    count = 0
    with IDS.open("w") as f:
        for doc in read(DENORMALISED):
            f.write(doc["_id"] + "\n")
            count += 1
    print(f"wrote {count} ids to {IDS}")


def subtract() -> None:
    before = {doc["_id"]: {key(diff) for diff in doc["diffs"]} for doc in read(IDENTIFIED)}
    works_per_field: Counter[str] = Counter()
    works = 0
    with OUT.open("w") as f:
        for doc in read(DENORMALISED):
            known = before.get(doc["_id"], set())
            rest = [diff for diff in doc["diffs"] if key(diff) not in known]
            if not rest:
                continue
            works += 1
            f.write(json.dumps({"_id": doc["_id"], "diffs": rest}) + "\n")
            works_per_field.update({field(diff) for diff in rest})
    print(f"{works} works differ after merging, written to {OUT}")
    for name, count in works_per_field.most_common():
        print(f"{count:>9} {name}")


if __name__ == "__main__":
    commands = {"ids": write_ids, "subtract": subtract}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        sys.exit(f"usage: {Path(__file__).name} ids|subtract")
    commands[sys.argv[1]]()
