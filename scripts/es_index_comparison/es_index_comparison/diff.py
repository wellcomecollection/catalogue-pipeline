from __future__ import annotations

import json
import re
from typing import Any

from deepdiff import DeepDiff
from deepdiff.helper import notpresent

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


def clean(value: Any, ignore: list[re.Pattern[str]], path: str = "") -> Any:
    """Return a copy without anything at an ignored path and without null-valued keys.

    Dropping null-valued keys makes a missing key and null equal.
    """
    if isinstance(value, dict):
        prefix = f"{path}." if path else ""
        return {
            key: clean(item, ignore, prefix + key)
            for key, item in value.items()
            if item is not None and not path_matches(prefix + key, ignore)
        }
    if isinstance(value, list):
        return [
            clean(item, ignore, f"{path}[{i}]")
            for i, item in enumerate(value)
            if not path_matches(f"{path}[{i}]", ignore)
        ]
    return value


def format_path(keys: list[str | int]) -> str:
    """Join DeepDiff path elements into a path such as `data.items[0].id`."""
    return "".join(f"[{key}]" if isinstance(key, int) else f".{key}" for key in keys).lstrip(".")


def deep_diff(
    a: Any,
    b: Any,
    unordered: list[re.Pattern[str]] | None = None,
    ignore: list[re.Pattern[str]] | None = None,
) -> list[dict]:
    """List the differences between two JSON values, as found by DeepDiff.

    Anything at a path matching `ignore` is left out of the comparison. Lists at
    paths matching `unordered`, and lists nested inside them, are compared
    regardless of order. Each difference has the DeepDiff change type, such as
    values_changed or iterable_item_added.
    """
    a, b = clean(a, ignore or []), clean(b, ignore or [])
    if a == b:
        return []

    options: dict[str, Any] = {}
    if unordered:
        options["report_repetition"] = True
        options["ignore_order_func"] = lambda level: path_matches(
            format_path(level.path(output_format="list")), unordered
        )

    diffs = []
    tree = DeepDiff(a, b, view="tree", **options)
    for change_type, changes in tree.items():
        for change in changes:
            left, right = change.t1, change.t2
            if change_type == "repetition_change":
                repetition = change.additional["repetition"]
                left, right = repetition["old_repeat"], repetition["new_repeat"]
            diffs.append(
                {
                    "path": format_path(change.path(output_format="list")),
                    "type": change_type,
                    "left": None if left is notpresent else left,
                    "right": None if right is notpresent else right,
                }
            )
    return sorted(diffs, key=lambda diff: (diff["path"], diff["type"]))
