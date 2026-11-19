from __future__ import annotations

import json
import random
import re
import runpy
from collections import Counter
from collections.abc import Callable
from concurrent.futures import ProcessPoolExecutor
from datetime import UTC, datetime
from functools import partial
from pathlib import Path

from rich.console import Console
from rich.markup import escape

from .buckets import bucket_files, read_bucket
from .diff import compile_ignore_patterns, deep_diff, truncate_val
from .source_config import ResolvedIndexSource

console = Console()

MAX_DIFFS_SHOWN = 500


def field_name(path: str, depth: int) -> str:
    """Return the first `depth` segments of a diff path, without list indexes."""
    return ".".join(re.sub(r"\[\d+\]", "", path).split(".")[:depth])


def load_normaliser(path: Path) -> Callable[[dict, dict], tuple[dict, dict] | None]:
    """Return the `normalise` function defined in a normaliser file."""
    return runpy.run_path(str(path))["normalise"]


def compare_bucket(
    bucket_id: int,
    folder_a: Path,
    folder_b: Path,
    ignore_fields: list[str],
    ignore_list_order: list[str],
    normaliser: Path | None,
    field_depth: int,
) -> dict:
    """Diff the documents of one hash bucket."""
    docs_a = read_bucket(folder_a, bucket_id)
    docs_b = read_bucket(folder_b, bucket_id)
    ignore = compile_ignore_patterns(ignore_fields)
    unordered = compile_ignore_patterns(ignore_list_order)
    normalise = load_normaliser(normaliser) if normaliser else None

    counts: Counter[str] = Counter()
    field_counts: Counter[str] = Counter()
    lines = []
    for doc_id in sorted(docs_a.keys() & docs_b.keys()):
        doc_a, doc_b = docs_a[doc_id], docs_b[doc_id]
        if normalise:
            pair = normalise(doc_a, doc_b)
            if pair is None:
                counts["skipped"] += 1
                continue
            doc_a, doc_b = pair
        diffs = deep_diff(doc_a, doc_b, unordered, ignore)
        if not diffs:
            counts["identical"] += 1
        else:
            counts["differing"] += 1
            counts["diff_entries"] += len(diffs)
            field_counts.update({field_name(d["path"], field_depth) for d in diffs})
            lines.append(json.dumps({"_id": doc_id, "diffs": diffs}, ensure_ascii=False))

    return {
        "docs_a": len(docs_a),
        "docs_b": len(docs_b),
        "only_in_a": list(docs_a.keys() - docs_b.keys()),
        "only_in_b": list(docs_b.keys() - docs_a.keys()),
        "counts": counts,
        "field_counts": field_counts,
        "lines": lines,
    }


def compare_indices(
    source_a: ResolvedIndexSource,
    source_b: ResolvedIndexSource,
    docs_dir: Path,
    ignore_fields: list[str],
    base_diff_folder: Path,
    sample_size: int,
    bucket_filter: set[int] | None = None,
    ignore_list_order: list[str] | None = None,
    normaliser: Path | None = None,
    field_depth: int = 2,
    workers: int = 1,
) -> dict:
    """Diff two fetched indices, one hash bucket per worker process at a time."""
    folder_a = docs_dir / source_a.storage_key
    folder_b = docs_dir / source_b.storage_key
    bucket_count = len(bucket_files(folder_a))
    if bucket_count == 0 or bucket_count != len(bucket_files(folder_b)):
        raise ValueError(
            f"{folder_a} and {folder_b} must hold the same number of bucket files. "
            "Run fetch for both indices with the same hash_bucket_count."
        )

    bucket_ids = sorted(bucket_filter) if bucket_filter is not None else list(range(bucket_count))
    label_a, label_b = escape(source_a.label), escape(source_b.label)
    compare = partial(
        compare_bucket,
        folder_a=folder_a,
        folder_b=folder_b,
        ignore_fields=ignore_fields,
        ignore_list_order=ignore_list_order or [],
        normaliser=normaliser,
        field_depth=field_depth,
    )

    only_in_a: list[str] = []
    only_in_b: list[str] = []
    counts: Counter[str] = Counter()
    field_counts: Counter[str] = Counter()

    base_diff_folder.mkdir(parents=True, exist_ok=True)
    with (
        (base_diff_folder / "diffs.jsonl").open("w") as jsonl,
        ProcessPoolExecutor(workers) as pool,
    ):
        results = zip(bucket_ids, pool.map(compare, bucket_ids))
        for position, (bucket_id, result) in enumerate(results, 1):
            only_in_a += result["only_in_a"]
            only_in_b += result["only_in_b"]
            counts += result["counts"]
            field_counts += result["field_counts"]
            jsonl.writelines(line + "\n" for line in result["lines"])
            console.log(
                f"[{position}/{len(bucket_ids)}] bucket={bucket_id:04d} "
                f"{label_a} docs={result['docs_a']} | {label_b} docs={result['docs_b']} | "
                f"differing={counts['differing']} | "
                f"only_in_a={len(only_in_a)} | only_in_b={len(only_in_b)}"
            )

    summary = {
        "index_a": source_a.label,
        "index_b": source_b.label,
        "buckets": bucket_ids,
        "skipped": counts["skipped"],
        "compared": counts["identical"] + counts["differing"],
        "identical": counts["identical"],
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
        f"Identical: {counts['identical']} | "
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
        console.print(f"Doc {escape(doc_id)} not found in diff set or is identical.")
        return False

    console.print(f"Total diffs for {escape(doc_id)}: {len(diffs)}")
    for d in diffs[:MAX_DIFFS_SHOWN]:
        console.print(f" - {escape(d['type']):15} @ {escape(d['path'])}")
        console.print(f"    {escape(label_a)}: {escape(truncate_val(d['left']))}")
        console.print(f"    {escape(label_b)}: {escape(truncate_val(d['right']))}")
    if len(diffs) > MAX_DIFFS_SHOWN:
        console.print(f"... {len(diffs) - MAX_DIFFS_SHOWN} additional diffs truncated ...")
    return True
