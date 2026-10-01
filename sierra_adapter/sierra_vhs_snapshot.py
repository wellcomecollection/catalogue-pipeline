"""Shared helpers for reading the Sierra VHS and its parquet snapshots.

The snapshots are written by catalogue_graph/scripts/snapshot_vhs.py: one row
per record with `id`, `version`, `s3_key` and `content` (the S3 body verbatim).
"""

import json
import re
from collections.abc import Iterable, Iterator

import boto3
import pyarrow.fs as pafs
import pyarrow.parquet as pq

TABLE_NAME = "vhs-sierra-sierra-adapter-20200604"
BUCKET = "wellcomecollection-vhs-sierra-sierra-adapter-20200604"
REGION = "eu-west-1"
SNAPSHOT_URI = (
    "s3://wellcomecollection-platform-infra/vhs_snapshots/"
    "vhs-sierra-sierra-adapter-20200604/pre-backstage-2026-10-01.parquet"
)

VHS_ID = re.compile(r"^[0-9]{7}$")


def open_snapshot(path: str, session: boto3.Session | None = None) -> pq.ParquetFile:
    """Open a local or s3:// parquet snapshot."""
    if not path.startswith("s3://"):
        return pq.ParquetFile(path)

    # Pass boto3's credentials through, because Arrow's own credential chain
    # does not follow every profile type boto3 does (SSO in particular).
    session = session or boto3.Session()
    creds = session.get_credentials().get_frozen_credentials()
    fs = pafs.S3FileSystem(
        access_key=creds.access_key,
        secret_key=creds.secret_key,
        session_token=creds.token,
        region=REGION,
    )
    return pq.ParquetFile(fs.open_input_file(path[len("s3://") :]))


def read_snapshot_index(snapshot: pq.ParquetFile) -> dict[str, tuple[int, str]]:
    """Map every id in the snapshot to its (version, s3_key)."""
    table = snapshot.read(columns=["id", "version", "s3_key"])
    return dict(
        zip(
            table.column("id").to_pylist(),
            zip(
                table.column("version").to_pylist(), table.column("s3_key").to_pylist()
            ),
        )
    )


def iter_snapshot_rows(
    snapshot: pq.ParquetFile, ids: Iterable[str], columns: list[str]
) -> Iterator[dict]:
    """Yield snapshot rows for the given ids, one row group at a time.

    The file is about 3 GB of mostly `content`, so this reads the id column
    first and decodes the other columns only for row groups holding a match.
    """
    wanted = set(ids)
    if not wanted:
        return
    columns = list(dict.fromkeys(["id", *columns]))

    for group in range(snapshot.num_row_groups):
        group_ids = snapshot.read_row_group(group, columns=["id"]).column("id")
        positions = [
            i for i, value in enumerate(group_ids.to_pylist()) if value in wanted
        ]
        if not positions:
            continue
        rows = snapshot.read_row_group(group, columns=columns).take(positions)
        yield from rows.to_pylist()


def iter_snapshot_batches(
    snapshot: pq.ParquetFile, ids: Iterable[str], batch_size: int = 5000
) -> Iterator[dict[str, str | None]]:
    """Yield {id: content} maps of roughly batch_size, so memory stays bounded."""
    batch: dict[str, str | None] = {}
    for row in iter_snapshot_rows(snapshot, ids, ["content"]):
        batch[row["id"]] = row["content"]
        if len(batch) >= batch_size:
            yield batch
            batch = {}
    if batch:
        yield batch


def read_id_file(path: str) -> list[str]:
    """Read one VHS id per line, ignoring blank lines and # comments."""
    ids: list[str] = []
    with open(path) as f:
        for line in f:
            value = line.split("#", 1)[0].strip()
            if not value:
                continue
            if not VHS_ID.match(value):
                raise ValueError(
                    f"{value!r} is not a 7-digit VHS id (no 'b' prefix, no check digit)"
                )
            ids.append(value)
    return list(dict.fromkeys(ids))


def write_id_file(path: str, ids: Iterable[str]) -> None:
    """Write ids one per line with no header, the shape start_reindex.py reads."""
    with open(path, "w") as f:
        f.writelines(f"{record_id}\n" for record_id in sorted(ids))


def bib_data(body: dict) -> str | None:
    bib = body.get("maybeBibRecord")
    return bib["data"] if bib else None


def changed_marc_tags(old_data: str | None, new_data: str | None) -> list[str]:
    """MARC tags (and top-level bib fields) whose content differs between two bibs."""
    old = json.loads(old_data) if old_data else {}
    new = json.loads(new_data) if new_data else {}

    def by_tag(bib: dict) -> dict[str, list[str]]:
        tags: dict[str, list[str]] = {}
        for field in bib.get("varFields") or []:
            tag = field.get("marcTag") or f"[{field.get('fieldTag', '?')}]"
            tags.setdefault(tag, []).append(json.dumps(field, sort_keys=True))
        return {tag: sorted(values) for tag, values in tags.items()}

    old_tags, new_tags = by_tag(old), by_tag(new)
    changed = {
        tag
        for tag in old_tags.keys() | new_tags.keys()
        if old_tags.get(tag) != new_tags.get(tag)
    }
    changed |= {
        key
        for key in (old.keys() | new.keys()) - {"varFields", "updatedDate"}
        if old.get(key) != new.get(key)
    }
    return sorted(changed)
