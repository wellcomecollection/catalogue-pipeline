#!/usr/bin/env python3
"""Extract CatalogueStatus for every live CALM record from the CALM VHS.

Scans the vhs-calm-adapter DynamoDB table, skips deletion tombstones, fetches
each record's payload from S3 and writes calm_statuses.csv with one row per
live record: record_id (the CALM RecordID), catalogue_status, ref_no,
alt_ref_no.

The CALM-side status is the only place the publish-to-web distinction
survives: in Axiell Collections both "Catalogued" and "Closed description"
records carry record_progress = CHECKED, so the tick list cannot be derived
from the AxC data alone. Tracked under wellcomecollection/platform#6503.

Run with a profile that can read the table and bucket:

    AWS_PROFILE=platform-read_only uv run --project catalogue_graph \
        python scripts/axiell_publish_to_web_import/extract_calm_statuses.py
"""

import argparse
import csv
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import boto3
from botocore.config import Config

TABLE = "vhs-calm-adapter"


def scan_live_rows(ddb) -> list[tuple[str, str, str]]:
    rows = []
    for page in ddb.get_paginator("scan").paginate(
        TableName=TABLE,
        ProjectionExpression="id, payload.#b, payload.#k, isDeleted",
        ExpressionAttributeNames={"#b": "bucket", "#k": "key"},
    ):
        for it in page["Items"]:
            if it.get("isDeleted", {}).get("BOOL"):
                continue
            payload = it["payload"]["M"]
            rows.append((it["id"]["S"], payload["bucket"]["S"], payload["key"]["S"]))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        default=str(Path(__file__).parent / "calm_statuses.csv"),
        help="Output CSV path (default: calm_statuses.csv beside this script)",
    )
    parser.add_argument("--workers", type=int, default=48)
    args = parser.parse_args()

    ddb = boto3.client("dynamodb", config=Config(max_pool_connections=args.workers))
    s3 = boto3.client(
        "s3",
        config=Config(max_pool_connections=args.workers, retries={"max_attempts": 8}),
    )

    rows = scan_live_rows(ddb)
    print(f"live rows: {len(rows)}", flush=True)

    def fetch(row: tuple[str, str, str]) -> tuple[str, str, str, str]:
        record_id, bucket, key = row
        body = s3.get_object(Bucket=bucket, Key=key)["Body"].read()
        data = json.loads(body).get("data", {})
        first = lambda field: (data.get(field) or [""])[0]
        return (record_id, first("CatalogueStatus"), first("RefNo"), first("AltRefNo"))

    with open(args.out, "w", newline="") as f, ThreadPoolExecutor(
        max_workers=args.workers
    ) as ex:
        writer = csv.writer(f)
        writer.writerow(["record_id", "catalogue_status", "ref_no", "alt_ref_no"])
        for i, result in enumerate(ex.map(fetch, rows, chunksize=200), 1):
            writer.writerow(result)
            if i % 25000 == 0:
                print(f"{i}/{len(rows)}", flush=True)
    print(f"wrote {args.out}", flush=True)


if __name__ == "__main__":
    main()
