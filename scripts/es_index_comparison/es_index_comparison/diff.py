from __future__ import annotations

import json
import re
from typing import Any

TRUNCATE_LEN = 300

PATTERN_TOKENS = {"[]": r"\[\d+\]", "**": ".*", "*": r"[^.\[]+"}


def compile_ignore_patterns(patterns: list[str]) -> list[re.Pattern[str]]:
    """Compile path patterns, where `[]` is any list index, `*` one segment and `**` any depth."""
    compiled = []
    for pattern in patterns:
        parts = re.split(r"(\[\]|\*\*|\*)", pattern)
        regex = "".join(PATTERN_TOKENS.get(part) or re.escape(part) for part in parts)
        compiled.append(re.compile(rf"^{regex}(?:$|[.\[])"))
    return compiled


def path_matches(path: str, compiled: list[re.Pattern[str]]) -> bool:
    return bool(path) and any(regex.match(path) for regex in compiled)


def truncate_val(value: Any) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    return text[:TRUNCATE_LEN] + "…" if len(text) > TRUNCATE_LEN else text


def sort_lists(value: Any, unordered: list[re.Pattern[str]], path: str = "") -> Any:
    """Return a copy in which every list at or below a path matching the patterns is sorted."""
    if isinstance(value, dict):
        prefix = f"{path}." if path else ""
        return {key: sort_lists(item, unordered, prefix + key) for key, item in value.items()}
    if isinstance(value, list):
        items = [sort_lists(item, unordered, f"{path}[{i}]") for i, item in enumerate(value)]
        if path_matches(path, unordered):
            items.sort(key=lambda item: json.dumps(item, sort_keys=True))
        return items
    return value


def deep_diff(a: Any, b: Any, path: str = "") -> list[dict[str, Any]]:
    """List the differences between two JSON values, treating a missing key and null as equal."""
    if type(a) is not type(b):
        return [{"path": path, "type": "type_mismatch", "left": a, "right": b}]
    if a == b:
        return []

    diffs: list[dict[str, Any]] = []
    if isinstance(a, dict):
        prefix = f"{path}." if path else ""
        for key in sorted(a.keys() - b.keys()):
            if a[key] is not None:
                diffs.append(
                    {
                        "path": prefix + key,
                        "type": "dict_key_removed",
                        "left": a[key],
                        "right": None,
                    }
                )
        for key in sorted(b.keys() - a.keys()):
            if b[key] is not None:
                diffs.append(
                    {"path": prefix + key, "type": "dict_key_added", "left": None, "right": b[key]}
                )
        for key in sorted(a.keys() & b.keys()):
            diffs += deep_diff(a[key], b[key], prefix + key)
    elif isinstance(a, list):
        if len(a) != len(b):
            diffs.append({"path": path, "type": "list_length", "left": len(a), "right": len(b)})
        for i, (left, right) in enumerate(zip(a, b)):
            diffs += deep_diff(left, right, f"{path}[{i}]")
    else:
        diffs.append({"path": path, "type": "scalar_change", "left": a, "right": b})
    return diffs
