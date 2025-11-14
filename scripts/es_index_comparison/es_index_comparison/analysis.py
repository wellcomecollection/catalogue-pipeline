from __future__ import annotations

import json
from pathlib import Path
from rich.console import Console
import pandas as pd
import polars as pl
from collections import Counter

from .diff import compile_ignore_patterns, aggregate_diffs, truncate_val
from .source_config import ResolvedIndexSource
from .parquet_io import PartitionedIndex
from rich.markup import escape as rich_escape

console = Console()


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
):
    console.log("Starting comparison phase (hash-bucket streaming)")

    reader_a = PartitionedIndex(parquet_root / source_a.storage_key)
    reader_b = PartitionedIndex(parquet_root / source_b.storage_key)

    if reader_a.hash_bucket_count != reader_b.hash_bucket_count:
        raise ValueError(
            "Hash bucket count mismatch between indices. Re-run convert stage so both indices use the same partitioning."
        )

    total_buckets = reader_a.hash_bucket_count
    hash_bucket_range = list(range(total_buckets))
    ignore_compiled = compile_ignore_patterns(ignore_fields)

    def extract_ids(df: pl.DataFrame) -> set:
        if df.height == 0 or "_id" not in df.columns:
            return set()
        return set(df.get_column("_id").to_list())

    diff_results: dict[str, list[dict]] = {}
    field_change_counter: Counter = Counter()
    identical_raw_total = 0
    identical_after_ignore_total = 0
    only_in_a: list[str] = []
    only_in_b: list[str] = []
    total_common = 0

    console.log(
        f"Total docs expected: {rich_escape(source_a.label)}={reader_a.total_docs} | {rich_escape(source_b.label)}={reader_b.total_docs}"
    )

    for idx, bucket_id in enumerate(hash_bucket_range, 1):
        df_a = reader_a.read_bucket(bucket_id)
        df_b = reader_b.read_bucket(bucket_id)
        ids_a = extract_ids(df_a)
        ids_b = extract_ids(df_b)
        bucket_only_in_a = sorted(ids_a - ids_b)
        bucket_only_in_b = sorted(ids_b - ids_a)
        bucket_common = ids_a & ids_b

        only_in_a.extend(bucket_only_in_a)
        only_in_b.extend(bucket_only_in_b)
        total_common += len(bucket_common)

        if bucket_common:
            sources_a = build_source_maps(df_a)
            sources_b = build_source_maps(df_b)
            agg = aggregate_diffs(bucket_common, sources_a, sources_b, ignore_compiled)
            diff_results.update(agg["diff_results"])
            field_change_counter.update(agg["field_change_counter"])
            identical_raw_total += agg["identical_raw"]
            identical_after_ignore_total += agg["identical_after_ignore"]

        console.log(
            f"[{idx}/{total_buckets}] bucket={bucket_id:04d} "
            f"{rich_escape(source_a.storage_key)} docs={len(ids_a)} | "
            f"{rich_escape(source_b.storage_key)} docs={len(ids_b)} | "
            f"common={len(bucket_common)} | diffs_collected={len(diff_results)}"
        )

    only_in_a.sort()
    only_in_b.sort()

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
        f"Identical raw: {identical_raw_total} | Identical after ignore: {identical_after_ignore_total} | Differing: {len(diff_results)}"
    )

    most_changed = field_change_counter.most_common(15)
    if most_changed:
        console.log("Top changed fields:")
        for f, c in most_changed:
            console.log(f"  {rich_escape(str(f))}: {c}")

    base_diff_folder.mkdir(parents=True, exist_ok=True)
    jsonl_path = base_diff_folder / "diffs.jsonl"
    summary_csv_path = base_diff_folder / "diff_summary.csv"
    field_freq_json_path = base_diff_folder / "field_frequency.json"

    with jsonl_path.open("w") as jf:
        for _id, diffs in diff_results.items():
            jf.write(json.dumps({"_id": _id, "diffs": diffs}, ensure_ascii=False) + "\n")
    console.log(f"Wrote diffs -> {jsonl_path}")

    import csv

    with summary_csv_path.open("w", newline="") as cf:
        w = csv.writer(cf)
        w.writerow(["_id", "diff_count", "top_level_changed_fields"])
        for _id, diffs in diff_results.items():
            top_fields = sorted(
                set(d["path"].split(".")[0].split("[")[0] for d in diffs if d.get("path"))
            )
            w.writerow([_id, len(diffs), ";".join(top_fields)])
    console.log(f"Wrote summary -> {summary_csv_path}")

    with field_freq_json_path.open("w") as ff:
        json.dump(dict(field_change_counter), ff, ensure_ascii=False, indent=2)
    console.log(f"Wrote field freq -> {field_freq_json_path}")

    # Meta parquet
    meta_rows = []
    for _id, diffs in diff_results.items():
        top_fields = sorted(
            set(d["path"].split(".")[0].split("[")[0] for d in diffs if d.get("path"))
        )
        meta_rows.append(
            {"_id": _id, "diff_count": len(diffs), "changed_top_level_fields": top_fields}
        )
    if meta_rows:
        meta_df = pd.DataFrame(meta_rows)
        pl.from_pandas(meta_df).write_parquet(base_diff_folder / "diff_meta.parquet")
        console.log("Wrote diff_meta.parquet")

    # Sampling written to markdown instead of console output
    from datetime import datetime, timezone

    sample_md_path = base_diff_folder / "sample_diffs.md"
    total_diff_entries = sum(len(v) for v in diff_results.values())
    distinct_field_paths = sorted(
        {d["path"] for diffs in diff_results.values() for d in diffs if d.get("path")}
    )
    top_level_fields_all = sorted({p.split(".")[0].split("[")[0] for p in distinct_field_paths})

    if diff_results:
        import random

        ids = list(diff_results.keys())
        random.shuffle(ids)
        ids = ids[:sample_size]
        with sample_md_path.open("w") as md:
            md.write("# Sample Differing Documents\n\n")
            md.write(f"Compared indices: `{source_a.label}` vs `{source_b.label}`\n\n")
            timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            md.write(f"Run timestamp: {timestamp}\n\n")
            md.write(f"Total differing documents: {len(diff_results)}\n")
            md.write(f"Total diff entries: {total_diff_entries}\n")
            md.write(f"Distinct changed field paths: {len(distinct_field_paths)}\n")
            md.write(
                f"Distinct top-level changed fields: {len(top_level_fields_all)} -> {', '.join(top_level_fields_all)}\n"
            )
            md.write(f"Sample size: {len(ids)} (configured: {sample_size})\n\n")
            for _id in ids:
                md.write(f"## Document `{_id}`\n\n")
                for d in diff_results[_id][:500]:
                    left = truncate_val(d.get("left"))
                    right = truncate_val(d.get("right"))
                    # Escape backticks in values for markdown formatting
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
        f"Summary: differing_docs={len(diff_results)} diff_entries={total_diff_entries} distinct_fields={len(distinct_field_paths)}"
    )

    return {
        "only_in_a": only_in_a,
        "only_in_b": only_in_b,
        "diff_results_count": len(diff_results),
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
