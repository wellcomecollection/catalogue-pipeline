#!/usr/bin/env python
"""Write pre-Backstage bib records back into the Sierra VHS as new versions.

For each id this reads the current DynamoDB row and S3 body, swaps in the
snapshot's maybeBibRecord, keeps the current items, holdings and orders, and
writes the result as version current+1. The pipeline indexes with an
external_gte version guard, so a restore has to move forward, never back.

Each write is a new S3 object at {id}/{version}/{uuid}.json, then a DynamoDB
update conditioned on the version and key we read, so a concurrent merger write
wins and the id is reported as a conflict rather than clobbered.

modifiedTime is kept at max(current modifiedTime, snapshot bib modifiedDate):
the transformer copies it to sourceModifiedTime, which is the ES external
version for source works, so it must not go backwards.

Nothing here notifies the pipeline. Feed the restored-ids file to
reindexer/scripts/start_reindex.py --src sierra --mode specific --input-file.

A restored bib carries the snapshot's (older) modifiedDate. The bibs merger
accepts any bib whose modifiedDate is the same or newer than the stored one,
so the next time Sierra sends this bib (an edit, a reharvest window, or the
queued Backstage updates when the bibs pause is lifted) the Backstage-era
version replaces the restore. Backing out only holds while Sierra stops
sending these bibs, or once Sierra itself has been reverted.

Dry run by default. Real writes need --execute and a write-capable profile.
"""

import json
import os
import sys
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime

import boto3
import click
from boto3.dynamodb.types import TypeDeserializer
from botocore.config import Config
from botocore.exceptions import ClientError

from sierra_vhs_snapshot import (
    BUCKET,
    REGION,
    SNAPSHOT_URI,
    TABLE_NAME,
    bib_data,
    changed_marc_tags,
    iter_snapshot_batches,
    open_snapshot,
    read_id_file,
    write_id_file,
)

# Outcomes
RESTORE = "restore"
RESTORED = "restored"
UNCHANGED = "no change"
CONFLICT = "conflict"
REFUSED = "refused"
ERROR = "error"


@dataclass
class Plan:
    id: str
    outcome: str
    reason: str = ""
    current_version: int | None = None
    new_version: int | None = None
    current_key: str | None = None
    new_key: str | None = None
    changed_tags: list[str] = field(default_factory=list)
    new_body: dict | None = None


def _parse_instant(value: str) -> datetime:
    return datetime.fromisoformat(value)


def _later(a: str, b: str) -> str:
    return a if _parse_instant(a) >= _parse_instant(b) else b


def build_restored_body(current: dict, snapshot: dict) -> dict:
    """The current body with the snapshot's bib, items/holdings/orders untouched."""
    snapshot_bib = snapshot["maybeBibRecord"]
    body = dict(current)
    body["maybeBibRecord"] = snapshot_bib
    body["modifiedTime"] = _later(current["modifiedTime"], snapshot_bib["modifiedDate"])
    return body


def plan_one(
    record_id: str,
    snapshot_content: str | None,
    row: dict | None,
    current: dict | None,
) -> Plan:
    """Decide what to do with one id, given its snapshot body and current state."""
    if snapshot_content is None:
        return Plan(record_id, REFUSED, "not in snapshot, or snapshot has no body")
    snapshot = json.loads(snapshot_content)
    snapshot_bib = snapshot.get("maybeBibRecord")
    if not snapshot_bib:
        return Plan(record_id, REFUSED, "snapshot has no bib record")
    if snapshot_bib.get("id") != record_id or snapshot.get("sierraId") != record_id:
        return Plan(record_id, REFUSED, "snapshot body is for a different id")
    if row is None or current is None:
        return Plan(record_id, REFUSED, "not in the live table")
    if current.get("sierraId") != record_id:
        return Plan(record_id, REFUSED, "current body is for a different id")

    current_version = int(row["version"])
    current_key = row["payload"]["key"]
    plan = Plan(
        record_id,
        RESTORE,
        current_version=current_version,
        current_key=current_key,
    )

    if row["payload"]["bucket"] != BUCKET:
        plan.outcome, plan.reason = (
            REFUSED,
            f"unexpected bucket {row['payload']['bucket']}",
        )
        return plan

    old_data, new_data = bib_data(current), snapshot_bib["data"]
    if old_data == new_data:
        plan.outcome, plan.reason = UNCHANGED, "current bib already matches snapshot"
        return plan

    current_bib = current.get("maybeBibRecord")
    if current_bib and _parse_instant(current_bib["modifiedDate"]) < _parse_instant(
        snapshot_bib["modifiedDate"]
    ):
        plan.outcome = REFUSED
        plan.reason = (
            "current bib is older than the snapshot's, so the VHS went backwards"
        )
        return plan

    plan.new_version = current_version + 1
    plan.new_key = f"{record_id}/{plan.new_version}/{uuid.uuid4()}.json"
    plan.changed_tags = changed_marc_tags(old_data, new_data)
    plan.new_body = build_restored_body(current, snapshot)
    return plan


_deserializer = TypeDeserializer()


def read_current(
    dynamodb, s3, table: str, record_id: str
) -> tuple[dict | None, dict | None]:
    """Read the row (strongly consistent) and the body it points at."""
    raw = dynamodb.get_item(
        TableName=table, Key={"id": {"S": record_id}}, ConsistentRead=True
    ).get("Item")
    if raw is None:
        return None, None
    item = {k: _deserializer.deserialize(v) for k, v in raw.items()}
    payload = item["payload"]
    body = s3.get_object(Bucket=payload["bucket"], Key=payload["key"])["Body"].read()
    return item, json.loads(body)


def apply_plan(dynamodb, s3, table: str, plan: Plan) -> Plan:
    """Write the new body, then point the row at it if nobody else has moved it."""
    s3.put_object(
        Bucket=BUCKET,
        Key=plan.new_key,
        Body=json.dumps(plan.new_body).encode("utf8"),
        ContentType="application/json",
    )
    try:
        dynamodb.update_item(
            TableName=table,
            Key={"id": {"S": plan.id}},
            UpdateExpression="SET #v = :new_version, #p.#k = :new_key",
            ConditionExpression="#v = :current_version AND #p.#k = :current_key",
            ExpressionAttributeNames={"#v": "version", "#p": "payload", "#k": "key"},
            ExpressionAttributeValues={
                ":new_version": {"N": str(plan.new_version)},
                ":new_key": {"S": plan.new_key},
                ":current_version": {"N": str(plan.current_version)},
                ":current_key": {"S": plan.current_key},
            },
        )
    except ClientError as error:
        if error.response["Error"]["Code"] != "ConditionalCheckFailedException":
            raise
        # The new object is left unreferenced; it is harmless and names itself.
        plan.outcome = CONFLICT
        plan.reason = f"row moved since it was read; orphan object {plan.new_key}"
        return plan

    plan.outcome = RESTORED
    return plan


def run(
    ids: list[str],
    snapshot_bodies: dict[str, str | None],
    dynamodb,
    s3,
    *,
    table: str = TABLE_NAME,
    execute: bool = False,
    workers: int = 16,
) -> list[Plan]:
    """Plan every id, and in execute mode apply the restores."""

    def process(record_id: str) -> Plan:
        try:
            snapshot_content = snapshot_bodies.get(record_id)
            if snapshot_content is None:
                return plan_one(record_id, None, None, None)
            row, current = read_current(dynamodb, s3, table, record_id)
            plan = plan_one(record_id, snapshot_content, row, current)
            if execute and plan.outcome == RESTORE:
                plan = apply_plan(dynamodb, s3, table, plan)
            return plan
        except Exception as error:  # noqa: BLE001 - report per id, keep going
            return Plan(record_id, ERROR, str(error))

    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(process, ids))


def run_from_snapshot(
    ids: list[str], snapshot_file, dynamodb, s3, **kwargs
) -> list[Plan]:
    """run() over the snapshot one batch at a time, so bodies are never all in memory."""
    plans: dict[str, Plan] = {}
    for batch in iter_snapshot_batches(snapshot_file, ids):
        for plan in run(list(batch), batch, dynamodb, s3, **kwargs):
            plan.new_body = None
            plans[plan.id] = plan
    for record_id in ids:
        if record_id not in plans:
            plans[record_id] = plan_one(record_id, None, None, None)
    return [plans[record_id] for record_id in ids]


def describe(plan: Plan) -> str:
    if plan.outcome in (RESTORE, RESTORED):
        tags = " ".join(plan.changed_tags[:12])
        more = (
            f" (+{len(plan.changed_tags) - 12})" if len(plan.changed_tags) > 12 else ""
        )
        return (
            f"{plan.id}  v{plan.current_version} -> v{plan.new_version}  "
            f"bib differs  tags: {tags}{more}"
        )
    version = f"  v{plan.current_version}" if plan.current_version is not None else ""
    return f"{plan.id}{version}  {plan.outcome}: {plan.reason}"


@click.command()
@click.option(
    "--ids-file",
    type=click.Path(exists=True, dir_okay=False),
    required=True,
    help="7-digit VHS ids, one per line (e.g. changed_bib.txt from backstage_changed_ids.py).",
)
@click.option(
    "--snapshot",
    default=SNAPSHOT_URI,
    show_default=True,
    help="Snapshot parquet, a local path or s3:// URI.",
)
@click.option(
    "--output",
    type=click.Path(dir_okay=False),
    default="restored_ids.txt",
    show_default=True,
    help="Where to write the restored ids (execute mode only), ready for a specific reindex.",
)
@click.option("--dry-run/--execute", default=True, show_default=True)
@click.option(
    "--yes", is_flag=True, help="Skip the confirmation prompt in --execute mode."
)
@click.option(
    "--limit", type=click.IntRange(min=1), help="Only process the first N ids."
)
@click.option("--workers", type=click.IntRange(1, 64), default=16, show_default=True)
@click.option("--table", default=TABLE_NAME, show_default=True)
def main(ids_file, snapshot, output, dry_run, yes, limit, workers, table):
    """Restore Sierra bibs in the VHS from a pre-Backstage snapshot."""
    ids = read_id_file(ids_file)
    if limit:
        ids = ids[:limit]
    if not ids:
        raise click.ClickException(f"{ids_file} has no ids")

    session = boto3.Session(region_name=REGION)
    config = Config(max_pool_connections=workers)
    dynamodb = session.client("dynamodb", config=config)
    s3 = session.client("s3", config=config)

    click.echo(f"Reading {len(ids):,} id(s) from {snapshot}", err=True)
    snapshot_file = open_snapshot(snapshot, session)

    # Plan everything read-only first, so the operator sees the whole picture
    # before confirming. Execute mode re-reads each row before it writes.
    plans = run_from_snapshot(
        ids, snapshot_file, dynamodb, s3, table=table, execute=False, workers=workers
    )
    for plan in plans:
        click.echo(describe(plan))

    counts = {
        o: sum(p.outcome == o for p in plans)
        for o in (RESTORE, UNCHANGED, REFUSED, ERROR)
    }
    click.echo("")
    for outcome, count in counts.items():
        click.echo(f"{outcome + ':':<12}{count:,}")

    if dry_run:
        click.echo("\nDry run: nothing written. Pass --execute to write.")
        return
    # Empty the output first, so it never lists ids restored by an earlier run.
    write_id_file(output, [])
    if counts[RESTORE] == 0:
        click.echo("\nNothing to restore.")
        return
    if not yes:
        click.confirm(
            f"Write {counts[RESTORE]:,} restored bib(s) to {table} and {BUCKET}?",
            abort=True,
        )

    to_restore = [p.id for p in plans if p.outcome == RESTORE]
    results = run_from_snapshot(
        to_restore,
        snapshot_file,
        dynamodb,
        s3,
        table=table,
        execute=True,
        workers=workers,
    )
    restored = [p.id for p in results if p.outcome == RESTORED]
    for plan in results:
        if plan.outcome != RESTORED:
            click.echo(describe(plan))

    write_id_file(output, restored)
    click.echo(
        f"\nrestored: {len(restored):,}, not restored on the write pass: "
        f"{len(results) - len(restored):,}. Restored ids in {os.path.abspath(output)}"
    )
    if len(restored) != len(results):
        sys.exit(1)


if __name__ == "__main__":
    main()
