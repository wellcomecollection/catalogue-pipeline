from __future__ import annotations

import csv
import json
import random
from collections import Counter
from pathlib import Path
from typing import TypedDict

import polars as pl
import pyarrow as pa
import pyarrow.parquet as pq
from rich.console import Console
from rich.markup import escape as rich_escape

from .diff import compile_ignore_patterns, deep_diff, path_ignored, truncate_val
from .parquet_io import PartitionedIndex
from .source_config import ResolvedIndexSource

console = Console()


class MetaWriterState(TypedDict):
    writer: pq.ParquetWriter | None
    path: Path


def build_source_maps(df_pl: pl.DataFrame | None) -> dict:
    if df_pl is None or df_pl.height == 0:
        return {}
    required_cols = {"_id", "_source"}
    if not required_cols.issubset(set(df_pl.columns)):
        return {}
    pdf = df_pl.select(["_id", "_source"]).to_pandas()
    out = {}
    for _id, _source in zip(pdf["_id"], pdf["_source"]):
        if isinstance(_source, str):
            try:
                _source = json.loads(_source)
            except json.JSONDecodeError:
                pass
        if not isinstance(_source, dict):
            _source = {"value": _source}
        out[_id] = _source
    return out


def compare_indices(
    source_a: ResolvedIndexSource,
    source_b: ResolvedIndexSource,
    parquet_root: Path,
    ignore_fields: list[str],
    base_diff_folder: Path,
    sample_size: int,
    bucket_filter: set[int] | None = None,
):
    console.log("Starting comparison phase (hash-bucket streaming)")

    reader_a = PartitionedIndex(parquet_root / source_a.storage_key)
    reader_b = PartitionedIndex(parquet_root / source_b.storage_key)

    if reader_a.hash_bucket_count != reader_b.hash_bucket_count:
        raise ValueError(
            "Hash bucket count mismatch between indices. Re-run convert stage so both indices use the same partitioning."
        )

    total_buckets = reader_a.hash_bucket_count
    bucket_ids = sorted(bucket_filter) if bucket_filter is not None else reader_a.iter_bucket_ids()
    ignore_compiled = compile_ignore_patterns(ignore_fields)

    def extract_ids(df: pl.DataFrame) -> set:
        if df.height == 0 or "_id" not in df.columns:
            return set()
        return set(df.get_column("_id").to_list())

    def record_diff(
        doc_id: str,
        diffs: list[dict],
        jsonl_handle,
        csv_writer,
        meta_rows_buffer: list[dict],
        field_counter: Counter,
        distinct_paths: set[str],
        top_level_paths: set[str],
    ) -> list[dict]:
        jsonl_handle.write(json.dumps({"_id": doc_id, "diffs": diffs}, ensure_ascii=False) + "\n")
        top_fields = sorted(
            set(d["path"].split(".")[0].split("[")[0] for d in diffs if d.get("path"))
        )
        csv_writer.writerow([doc_id, len(diffs), ";".join(top_fields)])
        meta_rows_buffer.append(
            {"_id": doc_id, "diff_count": len(diffs), "changed_top_level_fields": top_fields}
        )
        for entry in diffs:
            path = entry.get("path")
            if not path:
                continue
            distinct_paths.add(path)
            top_level_paths.add(path.split(".")[0].split("[")[0])
        for field in top_fields:
            field_counter[field] += 1
        return meta_rows_buffer

    def flush_meta_rows(meta_rows: list[dict], writer_state: MetaWriterState) -> list[dict]:
        if not meta_rows:
            return []
        table = pa.Table.from_pylist(meta_rows)
        if writer_state["writer"] is None:
            writer_state["writer"] = pq.ParquetWriter(writer_state["path"], table.schema)
        writer = writer_state["writer"]
        if writer is None:  # mypy safeguard
            raise RuntimeError("Parquet writer failed to initialize")
        writer.write_table(table)
        return []

    bucket_range_display = (
        f"subset ({len(bucket_ids)} of {total_buckets})"
        if bucket_filter is not None
        else f"all {total_buckets}"
    )
    console.log(
        f"Total docs expected: {rich_escape(source_a.label)}={reader_a.total_docs} | "
        f"{rich_escape(source_b.label)}={reader_b.total_docs} | Buckets processed: {bucket_range_display}"
    )

    base_diff_folder.mkdir(parents=True, exist_ok=True)
    jsonl_path = base_diff_folder / "diffs.jsonl"
    summary_csv_path = base_diff_folder / "diff_summary.csv"
    field_freq_json_path = base_diff_folder / "field_frequency.json"
    meta_path = base_diff_folder / "diff_meta.parquet"
    meta_writer_state: MetaWriterState = {"writer": None, "path": meta_path}

    distinct_field_paths: set[str] = set()
    top_level_fields_all: set[str] = set()
    field_change_counter: Counter = Counter()
    identical_raw_total = 0
    identical_after_ignore_total = 0
    only_in_a: list[str] = []
    only_in_b: list[str] = []
    total_common = 0
    total_differing_docs = 0
    total_diff_entries = 0
    meta_rows_buffer: list[dict] = []
    meta_batch_size = 500

    sample_docs: list[tuple[str, list[dict]]] = []
    seen_diff_docs = 0

    def update_sample(doc_id: str, diffs: list[dict]):
        nonlocal seen_diff_docs
        seen_diff_docs += 1
        if len(sample_docs) < sample_size:
            sample_docs.append((doc_id, diffs))
        else:
            idx = random.randint(0, seen_diff_docs - 1)
            if idx < sample_size:
                sample_docs[idx] = (doc_id, diffs)

    def process_doc(doc_id: str, sources_a: dict, sources_b: dict):
        nonlocal identical_raw_total, identical_after_ignore_total
        src_a = sources_a.get(doc_id)
        src_b = sources_b.get(doc_id)
        if src_a is None or src_b is None:
            return [{"path": "_source", "type": "missing", "left": src_a, "right": src_b}]
        raw = deep_diff(src_a, src_b)
        if not raw:
            identical_raw_total += 1
            return None
        filtered = [d for d in raw if not path_ignored(d.get("path", ""), ignore_compiled)]
        if not filtered:
            identical_after_ignore_total += 1
            return None
        return filtered

    with (
        jsonl_path.open("w") as jsonl_handle,
        summary_csv_path.open("w", newline="") as summary_handle,
    ):
        csv_writer = csv.writer(summary_handle)
        csv_writer.writerow(["_id", "diff_count", "top_level_changed_fields"])

        for idx, bucket_id in enumerate(bucket_ids, 1):
            df_a = reader_a.read_bucket(bucket_id)
            df_b = reader_b.read_bucket(bucket_id)
            ids_a = extract_ids(df_a)
            ids_b = extract_ids(df_b)
            bucket_only_in_a = sorted(ids_a - ids_b)
            bucket_only_in_b = sorted(ids_b - ids_a)
            bucket_common = sorted(ids_a & ids_b)

            only_in_a.extend(bucket_only_in_a)
            only_in_b.extend(bucket_only_in_b)
            total_common += len(bucket_common)

            if bucket_common:
                sources_a = build_source_maps(df_a)
                sources_b = build_source_maps(df_b)
                for doc_id in bucket_common:
                    diffs = process_doc(doc_id, sources_a, sources_b)
                    if not diffs:
                        continue
                    total_differing_docs += 1
                    total_diff_entries += len(diffs)
                    meta_rows_buffer = record_diff(
                        doc_id,
                        diffs,
                        jsonl_handle,
                        csv_writer,
                        meta_rows_buffer,
                        field_change_counter,
                        distinct_field_paths,
                        top_level_fields_all,
                    )
                    if len(meta_rows_buffer) >= meta_batch_size:
                        meta_rows_buffer = flush_meta_rows(meta_rows_buffer, meta_writer_state)
                    update_sample(doc_id, diffs)

            console.log(
                f"[{idx}/{len(bucket_ids)}] bucket={bucket_id:04d} "
                f"{rich_escape(source_a.storage_key)} docs={len(ids_a)} | "
                f"{rich_escape(source_b.storage_key)} docs={len(ids_b)} | "
                f"common={len(bucket_common)} | diffs_emitted={total_differing_docs}"
            )

    console.log(f"Wrote diffs -> {jsonl_path}")
    console.log(f"Wrote summary -> {summary_csv_path}")

    only_in_a.sort()
    only_in_b.sort()
    meta_rows_buffer = flush_meta_rows(meta_rows_buffer, meta_writer_state)
    if meta_writer_state["writer"] is not None:
        meta_writer_state["writer"].close()
        console.log("Wrote diff_meta.parquet")
    elif meta_path.exists():
        meta_path.unlink()

    with field_freq_json_path.open("w") as ff:
        json.dump(dict(field_change_counter), ff, ensure_ascii=False, indent=2)
    console.log(f"Wrote field freq -> {field_freq_json_path}")

    console.log(
        f"Common IDs processed: {total_common} | Only in {rich_escape(source_a.label)}: {len(only_in_a)} | Only in {rich_escape(source_b.label)}: {len(only_in_b)}"
    )
    if only_in_a:
        console.log(
            f"Only in {rich_escape(source_a.label)} count={len(only_in_a)} sample={rich_escape(', '.join(map(str, only_in_a[:10])))}"
        )
    if only_in_b:
        console.log(
            f"Only in {rich_escape(source_b.label)} count={len(only_in_b)} sample={rich_escape(', '.join(map(str, only_in_b[:10])))}"
        )

    console.log(
        f"Identical raw: {identical_raw_total} | Identical after ignore: {identical_after_ignore_total} | Differing: {total_differing_docs}"
    )

    most_changed = field_change_counter.most_common(15)
    if most_changed:
        console.log("Top changed fields:")
        for f, c in most_changed:
            console.log(f"  {rich_escape(str(f))}: {c}")

    from datetime import datetime, timezone

    sample_md_path = base_diff_folder / "sample_diffs.md"
    if total_differing_docs:
        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        random.shuffle(sample_docs)
        sample_subset = sample_docs[: min(sample_size, len(sample_docs))]
        with sample_md_path.open("w") as md:
            md.write("# Sample Differing Documents\n\n")
            md.write(f"Compared indices: `{source_a.label}` vs `{source_b.label}`\n\n")
            md.write(f"Run timestamp: {timestamp}\n\n")
            md.write(f"Total differing documents: {total_differing_docs}\n")
            md.write(f"Total diff entries: {total_diff_entries}\n")
            md.write(f"Distinct changed field paths: {len(distinct_field_paths)}\n")
            md.write(
                f"Distinct top-level changed fields: {len(top_level_fields_all)} -> {', '.join(sorted(top_level_fields_all))}\n"
            )
            md.write(f"Sample size: {len(sample_subset)} (configured: {sample_size})\n\n")
            for doc_id, diffs in sample_subset:
                md.write(f"## Document `{doc_id}`\n\n")
                for d in diffs[:500]:
                    left = truncate_val(d.get("left"))
                    right = truncate_val(d.get("right"))
                    left_md = left.replace("`", "\u200b`")
                    right_md = right.replace("`", "\u200b`")
                    md.write(
                        f"- **{d['type']}** @ `{d['path']}`\n  - {source_a.label}: `{left_md}`\n  - {source_b.label}: `{right_md}`\n"
                    )
                md.write("\n")
        console.log(f"Sample diffs written to {sample_md_path}")
    else:
        console.log("No differing documents to sample; skipping markdown export.")

    console.log(
        f"Summary: differing_docs={total_differing_docs} diff_entries={total_diff_entries} distinct_fields={len(distinct_field_paths)}"
    )

    return {
        "only_in_a": only_in_a,
        "only_in_b": only_in_b,
        "diff_results_count": total_differing_docs,
        "identical_raw": identical_raw_total,
        "identical_after_ignore": identical_after_ignore_total,
    }


def load_diff_jsonl(diff_dir: Path) -> dict[str, list[dict]]:
    path = diff_dir / "diffs.jsonl"
    if not path.exists():
        raise FileNotFoundError(f"Diff JSONL not found: {path}. Run compare stage first.")
    out: dict[str, list[dict]] = {}
    with path.open("r") as f:
        for line in f:
            rec = json.loads(line)
            out[rec["_id"]] = rec["diffs"]
    return out


def show_single_diff(doc_id: str, diff_dir: Path, label_a: str, label_b: str, limit: int = 500):
    diffs_map = load_diff_jsonl(diff_dir)
    if doc_id not in diffs_map:
        console.print(f"Doc {doc_id} not found in diff set or is identical post-filter.")
        return 1
    diffs = diffs_map[doc_id]
    console.print(f"Total diffs for {rich_escape(doc_id)}: {len(diffs)} (showing up to {limit})")
    for d in diffs[:limit]:
        left = truncate_val(d.get("left"))
        right = truncate_val(d.get("right"))
        console.print(
            f" - {rich_escape(d['type']):15} @ {rich_escape(d['path'])}\n    {rich_escape(label_a)}: {rich_escape(left)}\n    {rich_escape(label_b)}: {rich_escape(right)}",
            markup=True,
        )
    if len(diffs) > limit:
        console.print(f"... {len(diffs) - limit} additional diffs truncated ...")
    return 0
