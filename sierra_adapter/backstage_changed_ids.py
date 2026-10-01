#!/usr/bin/env python
"""Compare the live Sierra VHS with a parquet snapshot and list the ids that moved.

Read-only. Scans the DynamoDB index (id, version, payload) and compares each
row's version with the snapshot's, writing one id file per class:

  changed.txt         version moved forward since the snapshot
  new.txt             in the table but not in the snapshot
  missing.txt         in the snapshot but not in the table
  moved_backward.txt  version lower than the snapshot's (should never happen)

With --classify, each changed id is split by whether its bib changed:

  changed_bib.txt     maybeBibRecord.data differs from the snapshot
  changed_other.txt   bib identical; only items, holdings or orders moved
  changed_classified.csv  per-id detail, including which MARC tags changed

Every .txt file holds 7-digit VHS ids (no 'b' prefix, no check digit), one per
line with no header. That is the form reindexer/scripts/start_reindex.py
--mode specific --input-file expects for --src sierra.

Run with a read-only profile, for example:

  uv run python backstage_changed_ids.py --snapshot /path/to/snapshot.parquet \\
      --output-dir ./changed --classify
"""

import csv
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import boto3
import click
from botocore.config import Config

from sierra_vhs_snapshot import (
    REGION,
    SNAPSHOT_URI,
    TABLE_NAME,
    bib_data,
    changed_marc_tags,
    iter_snapshot_batches,
    open_snapshot,
    read_snapshot_index,
    write_id_file,
)


@dataclass(frozen=True)
class LiveRow:
    id: str
    version: int
    bucket: str
    key: str


@dataclass
class Comparison:
    changed: list[str]
    new: list[str]
    missing: list[str]
    moved_backward: list[str]


def scan_table(client, table_name: str, segments: int) -> dict[str, LiveRow]:
    """Parallel segmented scan of the VHS index, projecting only what we need."""

    def scan_segment(segment: int) -> list[LiveRow]:
        rows = []
        paginator = client.get_paginator("scan")
        for page in paginator.paginate(
            TableName=table_name,
            Segment=segment,
            TotalSegments=segments,
            ProjectionExpression="#i, #v, #p",
            ExpressionAttributeNames={"#i": "id", "#v": "version", "#p": "payload"},
        ):
            for item in page["Items"]:
                payload = item["payload"]["M"]
                rows.append(
                    LiveRow(
                        id=item["id"]["S"],
                        version=int(item["version"]["N"]),
                        bucket=payload["bucket"]["S"],
                        key=payload["key"]["S"],
                    )
                )
        return rows

    with ThreadPoolExecutor(max_workers=segments) as pool:
        results = list(pool.map(scan_segment, range(segments)))
    rows = {row.id: row for segment_rows in results for row in segment_rows}
    if not rows:
        raise click.ClickException(f"{table_name} returned 0 rows, which is an error")
    return rows


def compare(live: dict[str, int], snapshot: dict[str, int]) -> Comparison:
    """Split ids by how their version moved. Both maps are id -> version."""
    changed, moved_backward = [], []
    for record_id in live.keys() & snapshot.keys():
        if live[record_id] > snapshot[record_id]:
            changed.append(record_id)
        elif live[record_id] < snapshot[record_id]:
            moved_backward.append(record_id)
    return Comparison(
        changed=sorted(changed),
        new=sorted(live.keys() - snapshot.keys()),
        missing=sorted(snapshot.keys() - live.keys()),
        moved_backward=sorted(moved_backward),
    )


def _keyset(records: dict | None) -> str:
    return json.dumps(records or {}, sort_keys=True)


def classify_one(current: dict, snapshot: dict) -> dict:
    """Describe how one record's body differs from its snapshot body."""
    old_data, new_data = bib_data(snapshot), bib_data(current)
    return {
        "bib_changed": old_data != new_data,
        "items_changed": _keyset(snapshot.get("itemRecords"))
        != _keyset(current.get("itemRecords")),
        "holdings_changed": _keyset(snapshot.get("holdingsRecords"))
        != _keyset(current.get("holdingsRecords")),
        "orders_changed": _keyset(snapshot.get("orderRecords"))
        != _keyset(current.get("orderRecords")),
        "changed_tags": " ".join(changed_marc_tags(old_data, new_data))
        if old_data != new_data
        else "",
        "snapshot_bib_modified": (snapshot.get("maybeBibRecord") or {}).get(
            "modifiedDate", ""
        ),
        "current_bib_modified": (current.get("maybeBibRecord") or {}).get(
            "modifiedDate", ""
        ),
    }


def classify(
    s3_client,
    snapshot_file,
    changed: list[str],
    live_rows: dict[str, LiveRow],
    snapshot_index: dict[str, tuple[int, str]],
    workers: int,
) -> list[dict]:
    """Fetch current bodies from S3 and snapshot bodies from the parquet file.

    The snapshot body is read from the parquet content column rather than its
    old S3 key, because prune_sierra_adapter_s3_entries.py deletes superseded
    versions from the bucket.
    """

    def fetch(record_id: str, snapshot_bodies: dict[str, str | None]) -> dict:
        row = live_rows[record_id]
        result = {
            "id": record_id,
            "snapshot_version": snapshot_index[record_id][0],
            "current_version": row.version,
        }
        try:
            snapshot_content = snapshot_bodies.get(record_id)
            if snapshot_content is None:
                raise ValueError("snapshot has no content for this id")
            body = s3_client.get_object(Bucket=row.bucket, Key=row.key)["Body"].read()
            result.update(classify_one(json.loads(body), json.loads(snapshot_content)))
            result["error"] = ""
        except Exception as error:  # noqa: BLE001 - report per id, keep going
            result["error"] = str(error)
        return result

    results: list[dict] = []
    seen: set[str] = set()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        # One batch of snapshot bodies in memory at a time.
        for batch in iter_snapshot_batches(snapshot_file, changed):
            results.extend(pool.map(lambda i, b=batch: fetch(i, b), batch))
            seen |= batch.keys()
        results.extend(fetch(i, {}) for i in changed if i not in seen)
    return sorted(results, key=lambda r: r["id"])


CSV_FIELDS = [
    "id",
    "snapshot_version",
    "current_version",
    "bib_changed",
    "items_changed",
    "holdings_changed",
    "orders_changed",
    "changed_tags",
    "snapshot_bib_modified",
    "current_bib_modified",
    "error",
]


@click.command()
@click.option(
    "--snapshot",
    default=SNAPSHOT_URI,
    show_default=True,
    help="Snapshot parquet, a local path or s3:// URI.",
)
@click.option(
    "--output-dir",
    type=click.Path(file_okay=False),
    required=True,
    help="Directory for the id files and CSV. Created if missing.",
)
@click.option(
    "--classify",
    "classify_ids",
    is_flag=True,
    help="Fetch the current and snapshot bodies of changed ids and split them by whether the bib changed.",
)
@click.option("--table", default=TABLE_NAME, show_default=True)
@click.option("--segments", type=click.IntRange(1, 64), default=16, show_default=True)
@click.option(
    "--workers",
    type=click.IntRange(1, 128),
    default=32,
    show_default=True,
    help="Concurrent S3 reads when classifying.",
)
def main(snapshot, output_dir, classify_ids, table, segments, workers):
    """List Sierra VHS ids whose version moved since the snapshot.

    Output ids are 7-digit VHS ids, not b-numbers. start_reindex.py --src sierra
    wants these, one per line.
    """
    started = time.time()
    session = boto3.Session(region_name=REGION)
    config = Config(max_pool_connections=max(segments, workers))

    snapshot_file = open_snapshot(snapshot, session)
    snapshot_index = read_snapshot_index(snapshot_file)
    click.echo(f"Snapshot: {len(snapshot_index):,} rows from {snapshot}", err=True)

    live_rows = scan_table(session.client("dynamodb", config=config), table, segments)
    click.echo(f"Table:    {len(live_rows):,} rows from {table}", err=True)

    result = compare(
        {i: r.version for i, r in live_rows.items()},
        {i: v for i, (v, _) in snapshot_index.items()},
    )

    os.makedirs(output_dir, exist_ok=True)
    for name in ("changed", "new", "missing", "moved_backward"):
        write_id_file(os.path.join(output_dir, f"{name}.txt"), getattr(result, name))

    click.echo("")
    click.echo(f"changed:        {len(result.changed):,}")
    click.echo(f"new:            {len(result.new):,}")
    click.echo(f"missing:        {len(result.missing):,}")
    click.echo(f"moved_backward: {len(result.moved_backward):,}")
    if result.moved_backward:
        click.secho(
            "WARNING: some versions went backwards since the snapshot. The VHS "
            "should never do this; investigate before restoring anything.",
            fg="red",
        )

    if classify_ids and result.changed:
        rows = classify(
            session.client("s3", config=config),
            snapshot_file,
            result.changed,
            live_rows,
            snapshot_index,
            workers,
        )
        with open(os.path.join(output_dir, "changed_classified.csv"), "w") as f:
            writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
            writer.writeheader()
            writer.writerows(rows)

        ok = [r for r in rows if not r["error"]]
        bib = [r["id"] for r in ok if r["bib_changed"]]
        other = [r["id"] for r in ok if not r["bib_changed"]]
        errors = [r["id"] for r in rows if r["error"]]
        write_id_file(os.path.join(output_dir, "changed_bib.txt"), bib)
        write_id_file(os.path.join(output_dir, "changed_other.txt"), other)

        click.echo(f"  bib changed:            {len(bib):,}")
        click.echo(f"  only items/holdings/orders: {len(other):,}")
        if errors:
            click.secho(
                f"  could not classify:     {len(errors):,} (see the error column)",
                fg="yellow",
            )

    click.echo(f"\nWrote id files to {output_dir} in {time.time() - started:.0f}s")
    if result.moved_backward:
        sys.exit(2)


if __name__ == "__main__":
    main()
