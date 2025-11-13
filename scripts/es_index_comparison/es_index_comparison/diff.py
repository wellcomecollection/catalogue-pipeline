from __future__ import annotations
from .transforms import TRANSFORMS
import re
import json
from typing import Any, Dict, List
import numpy as np
from collections import Counter

TRUNCATE_LEN = 300

# ---------------- Ignore Pattern Handling ----------------


def compile_ignore_patterns(patterns: list[str]):
    compiled = []
    for p in patterns:
        esc = ""
        i = 0
        while i < len(p):
            if p.startswith("[]", i):
                esc += r"\[\d+\]"
                i += 2
                continue
            ch = p[i]
            if ch == "*":
                if i + 1 < len(p) and p[i + 1] == "*":
                    esc += ".*"
                    i += 2
                else:
                    esc += r"[^.\[]+"
                    i += 1
            else:
                esc += re.escape(ch)
                i += 1
        regex = re.compile(r"^" + esc + r"(?:$|[.\[])")
        compiled.append((p, regex))
    return compiled


def path_ignored(path: str, compiled) -> bool:
    if not path:
        return False
    for raw, rgx in compiled:
        if rgx.match(path):
            return True
    return False


# ---------------- Deep Diff ----------------


def normalize_value(v: Any) -> Any:
    if isinstance(v, np.ndarray):
        return [normalize_value(x) for x in v.tolist()]
    if isinstance(v, list):
        return [normalize_value(x) for x in v]
    if isinstance(v, dict):
        return {k: normalize_value(val) for k, val in v.items()}
    return v


def truncate_val(v: Any) -> str:
    v = normalize_value(v)
    if not isinstance(v, str):
        try:
            s = json.dumps(v, ensure_ascii=False)
        except TypeError:
            s = str(v)
    else:
        s = v
    return (s[:TRUNCATE_LEN] + "…") if len(s) > TRUNCATE_LEN else s


def deep_diff(a: Any, b: Any, path: str = "") -> List[Dict[str, Any]]:
    diffs: List[Dict[str, Any]] = []
    a = normalize_value(a)
    b = normalize_value(b)
    
    if path in TRANSFORMS:
        a, b = TRANSFORMS[path](a, b)

    if type(a) is not type(b):
        diffs.append({"path": path, "type": "type_mismatch", "left": a, "right": b})
        return diffs

    if isinstance(a, dict):
        a_keys = set(a.keys())
        b_keys = set(b.keys())
        for k in sorted(a_keys - b_keys):
            val = a[k]
            if val is None:
                continue
            diffs.append(
                {
                    "path": f"{path}.{k}" if path else k,
                    "type": "dict_key_removed",
                    "left": val,
                    "right": None,
                }
            )
        for k in sorted(b_keys - a_keys):
            val = b[k]
            if val is None:
                continue
            diffs.append(
                {
                    "path": f"{path}.{k}" if path else k,
                    "type": "dict_key_added",
                    "left": None,
                    "right": val,
                }
            )
        for k in sorted(a_keys & b_keys):
            diffs.extend(deep_diff(a[k], b[k], f"{path}.{k}" if path else k))
        return diffs

    if isinstance(a, list):
        if len(a) != len(b):
            diffs.append({"path": path, "type": "list_length", "left": len(a), "right": len(b)})
        for i, (av, bv) in enumerate(zip(a, b)):
            diffs.extend(deep_diff(av, bv, f"{path}[{i}]"))
        return diffs

    if a != b:
        diffs.append({"path": path, "type": "scalar_change", "left": a, "right": b})
    return diffs


# ---------------- Aggregation ----------------


def aggregate_diffs(common_ids, sources_a, sources_b, ignore_compiled):
    diff_results: Dict[str, list[dict]] = {}
    field_change_counter = Counter()
    identical_raw = 0
    identical_after_ignore = 0

    for _id in common_ids:
        src_a = sources_a.get(_id)
        src_b = sources_b.get(_id)
        if src_a is None or src_b is None:
            diff_results[_id] = [
                {"path": "_source", "type": "missing", "left": src_a, "right": src_b}
            ]
            continue
        raw = deep_diff(src_a, src_b)
        if not raw:
            identical_raw += 1
            continue
        filtered = [d for d in raw if not path_ignored(d.get("path", ""), ignore_compiled)]
        if not filtered:
            identical_after_ignore += 1
            continue
        diff_results[_id] = filtered
        top_fields = set(d["path"].split(".")[0].split("[")[0] for d in filtered if d.get("path"))
        for f in top_fields:
            field_change_counter[f] += 1

    return {
        "diff_results": diff_results,
        "field_change_counter": field_change_counter,
        "identical_raw": identical_raw,
        "identical_after_ignore": identical_after_ignore,
    }
