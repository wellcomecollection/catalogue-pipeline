from __future__ import annotations

import json
import random
import re
from collections import Counter
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path

from rich.console import Console
from rich.markup import escape

from .diff import compile_ignore_patterns, deep_diff, path_matches, sort_lists, truncate_val
from .parquet_io import PartitionedIndex
from .source_config import ResolvedIndexSource

console = Console()

MAX_DIFFS_SHOWN = 500


def field_name(path: str, depth: int) -> str:
    """Return the first `depth` segments of a diff path, without list indexes."""
    return ".".join(re.sub(r"\[\d+\]", "", path).split(".")[:depth])


def load_sources(reader: PartitionedIndex, bucket_id: int) -> dict[str, dict]:
    """Return the documents of one bucket as a mapping of _id to _source."""
    df = reader.read_bucket(bucket_id)
    if df.height == 0:
        return {}
    return dict(df.select("_id", "_source").iter_rows())


def compare_indices(
    source_a: ResolvedIndexSource,
    source_b: ResolvedIndexSource,
    parquet_root: Path,
    ignore_fields: list[str],
    base_diff_folder: Path,
    sample_size: int,
    bucket_filter: set[int] | None = None,
    ignore_list_order: list[str] | None = None,
    normalise: Callable[[dict, dict], tuple[dict, dict] | None] | None = None,
    field_depth: int = 2,
) -> dict:
    reader_a = PartitionedIndex(parquet_root / source_a.storage_key)
    reader_b = PartitionedIndex(parquet_root / source_b.storage_key)
    if reader_a.hash_bucket_count != reader_b.hash_bucket_count:
        raise ValueError(
            "Hash bucket count mismatch between indices. Re-run convert stage so both indices use the same partitioning."
        )

    bucket_ids = sorted(bucket_filter) if bucket_filter is not None else reader_a.iter_bucket_ids()
    ignore = compile_ignore_patterns(ignore_fields)
    unordered = compile_ignore_patterns(ignore_list_order or [])
    label_a, label_b = escape(source_a.label), escape(source_b.label)

    only_in_a: list[str] = []
    only_in_b: list[str] = []
    counts: Counter[str] = Counter()
    field_counts: Counter[str] = Counter()

    base_diff_folder.mkdir(parents=True, exist_ok=True)
    with (base_diff_folder / "diffs.jsonl").open("w") as jsonl:
        for position, bucket_id in enumerate(bucket_ids, 1):
            docs_a = load_sources(reader_a, bucket_id)
            docs_b = load_sources(reader_b, bucket_id)
            only_in_a += docs_a.keys() - docs_b.keys()
            only_in_b += docs_b.keys() - docs_a.keys()

            for doc_id in sorted(docs_a.keys() & docs_b.keys()):
                doc_a, doc_b = docs_a[doc_id], docs_b[doc_id]
                if normalise:
                    pair = normalise(doc_a, doc_b)
                    if pair is None:
                        counts["skipped"] += 1
                        continue
                    doc_a, doc_b = pair
                if unordered:
                    doc_a, doc_b = sort_lists(doc_a, unordered), sort_lists(doc_b, unordered)
                all_diffs = deep_diff(doc_a, doc_b)
                diffs = [d for d in all_diffs if not path_matches(d["path"], ignore)]
                if not all_diffs:
                    counts["identical_raw"] += 1
                elif not diffs:
                    counts["identical_after_ignore"] += 1
                else:
                    counts["differing"] += 1
                    counts["diff_entries"] += len(diffs)
                    field_counts.update({field_name(d["path"], field_depth) for d in diffs})
                    record = {"_id": doc_id, "diffs": diffs}
                    jsonl.write(json.dumps(record, ensure_ascii=False) + "\n")

            console.log(
                f"[{position}/{len(bucket_ids)}] bucket={bucket_id:04d} "
                f"{label_a} docs={len(docs_a)} | {label_b} docs={len(docs_b)} | "
                f"differing={counts['differing']} | "
                f"only_in_a={len(only_in_a)} | only_in_b={len(only_in_b)}"
            )

    compared = counts["identical_raw"] + counts["identical_after_ignore"] + counts["differing"]
    summary = {
        "index_a": source_a.label,
        "index_b": source_b.label,
        "buckets": bucket_ids,
        "skipped": counts["skipped"],
        "compared": compared,
        "identical_raw": counts["identical_raw"],
        "identical_after_ignore": counts["identical_after_ignore"],
        "differing": counts["differing"],
        "diff_entries": counts["diff_entries"],
        "only_in_a": sorted(only_in_a),
        "only_in_b": sorted(only_in_b),
    }
    with (base_diff_folder / "summary.json").open("w") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    with (base_diff_folder / "field_frequency.json").open("w") as f:
        json.dump(dict(field_counts.most_common()), f, ensure_ascii=False, indent=2)

    console.log(
        f"Identical raw: {counts['identical_raw']} | "
        f"Identical after ignore: {counts['identical_after_ignore']} | "
        f"Differing: {counts['differing']} ({counts['diff_entries']} diff entries)"
    )
    console.log(f"Only in {label_a}: {len(only_in_a)} sample={escape(str(only_in_a[:10]))}")
    console.log(f"Only in {label_b}: {len(only_in_b)} sample={escape(str(only_in_b[:10]))}")
    for field, count in field_counts.most_common(15):
        console.log(f"  {escape(field)}: {count}")

    if counts["differing"]:
        write_sample(base_diff_folder, summary, field_counts, sample_size, source_a, source_b)
    console.log(f"Wrote diff artifacts -> {base_diff_folder}")

    return summary


def write_sample(
    diff_dir: Path,
    summary: dict,
    field_counts: Counter[str],
    sample_size: int,
    source_a: ResolvedIndexSource,
    source_b: ResolvedIndexSource,
) -> None:
    """Write a random sample of the differing documents to sample_diffs.md."""
    differing = summary["differing"]
    chosen = set(random.sample(range(differing), min(sample_size, differing)))

    with (diff_dir / "diffs.jsonl").open() as jsonl, (diff_dir / "sample_diffs.md").open("w") as md:
        md.write("# Sample Differing Documents\n\n")
        md.write(f"Compared indices: `{source_a.label}` vs `{source_b.label}`\n\n")
        md.write(f"Run timestamp: {datetime.now(UTC):%Y-%m-%dT%H:%M:%SZ}\n\n")
        md.write(f"Total differing documents: {differing}\n")
        md.write(f"Total diff entries: {summary['diff_entries']}\n")
        md.write(f"Changed fields: {', '.join(sorted(field_counts))}\n")
        md.write(f"Sample size: {len(chosen)} (configured: {sample_size})\n\n")

        for line_number, line in enumerate(jsonl):
            if line_number not in chosen:
                continue
            record = json.loads(line)
            md.write(f"## Document `{record['_id']}`\n\n")
            for d in record["diffs"][:MAX_DIFFS_SHOWN]:
                left = truncate_val(d["left"]).replace("`", "​`")
                right = truncate_val(d["right"]).replace("`", "​`")
                md.write(f"- **{d['type']}** @ `{d['path']}`\n")
                md.write(f"  - {source_a.label}: `{left}`\n")
                md.write(f"  - {source_b.label}: `{right}`\n")
            md.write("\n")


def show_single_diff(doc_id: str, diff_dir: Path, label_a: str, label_b: str) -> bool:
    """Print the diffs of one document, returning False when it has none recorded."""
    with (diff_dir / "diffs.jsonl").open() as jsonl:
        records = map(json.loads, jsonl)
        diffs = next((r["diffs"] for r in records if r["_id"] == doc_id), None)
    if diffs is None:
        console.print(f"Doc {escape(doc_id)} not found in diff set or is identical post-filter.")
        return False

    console.print(f"Total diffs for {escape(doc_id)}: {len(diffs)}")
    for d in diffs[:MAX_DIFFS_SHOWN]:
        console.print(f" - {escape(d['type']):15} @ {escape(d['path'])}")
        console.print(f"    {escape(label_a)}: {escape(truncate_val(d['left']))}")
        console.print(f"    {escape(label_b)}: {escape(truncate_val(d['right']))}")
    if len(diffs) > MAX_DIFFS_SHOWN:
        console.print(f"... {len(diffs) - MAX_DIFFS_SHOWN} additional diffs truncated ...")
    return True
