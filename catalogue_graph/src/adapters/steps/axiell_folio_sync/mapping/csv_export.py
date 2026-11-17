"""
The mapping CSVs in ``docs/axiell-folio-mappings``, one per AxC to FOLIO value
mapping, generated from ``config.py`` so they cannot drift from the code:
material type, item status, loan type, access statistical code and locations.

Regenerate after changing a table, from ``catalogue_graph/``::

    uv run python -m adapters.steps.axiell_folio_sync.mapping.csv_export

A test compares the files on disk with :func:`render`, so a table changed
without regenerating fails CI.
"""

from __future__ import annotations

import csv
import io
import sys
from pathlib import Path

from .config import (
    ACCESS_ITEM_STATUS,
    ACCESS_STATISTICAL_CODE,
    DEFAULT_ITEM_STATUS,
    DEFAULT_LOAN_TYPE,
    LOCATION_INSTITUTION,
    LOCATION_RULES,
    MATERIAL_TYPE,
    USE_RESTRICTION_LOAN_TYPE,
    VERSION,
)

# catalogue_graph/docs/axiell-folio-mappings, from this file's place in src.
DEFAULT_DIRECTORY = (
    Path(__file__).resolve().parents[5] / "docs" / "axiell-folio-mappings"
)


def _csv(rows: list[list[str]]) -> str:
    out = io.StringIO()
    csv.writer(out, lineterminator="\n").writerows(rows)
    return out.getvalue()


def _material_type() -> str:
    rows = [["AxC object category (655$a)", "FOLIO material type", "Notes"]]
    rows += [[key, value, "Case-insensitive."] for key, value in MATERIAL_TYPE.items()]
    rows += [
        ["(absent)", "", "Record fails: required, no default."],
        ["(unlisted)", "", "Record fails: the table is the agreed vocabulary."],
    ]
    return _csv(rows)


# How the item status behaves on update, from upsert.entities._payload_for_update.
_ON_UPDATE = (
    "On update, applied only while FOLIO holds a status the sync sets; a"
    " circulation status is kept."
)


def _item_status() -> str:
    rows = [["AxC access category (506$f)", "FOLIO item status", "Notes"]]
    rows += [
        [key, value, f"Case-insensitive. {_ON_UPDATE}"]
        for key, value in ACCESS_ITEM_STATUS.items()
    ]
    rows += [
        ["(absent)", DEFAULT_ITEM_STATUS, _ON_UPDATE],
        ["(unlisted)", "", "Record fails."],
    ]
    return _csv(rows)


def _loan_type() -> str:
    rows = [["AxC use restriction (540$a)", "FOLIO loan type", "Notes"]]
    rows += [
        [key, value, "Case-insensitive."]
        for key, value in USE_RESTRICTION_LOAN_TYPE.items()
    ]
    rows.append(
        [
            "(absent or unlisted)",
            DEFAULT_LOAN_TYPE,
            "Never passed to the tenant as a loan type name.",
        ]
    )
    return _csv(rows)


def _statistical_code() -> str:
    rows = [
        [
            "AxC access category (506$f)",
            "AxC use restriction (540$a)",
            "FOLIO statistical code",
            "Notes",
        ]
    ]
    rows += [
        [category, term, code, "Item only. Both case-insensitive."]
        for category, by_term in ACCESS_STATISTICAL_CODE.items()
        for term, code in by_term.items()
    ]
    rows.append(
        [
            "(any other pair, or either absent)",
            "",
            "",
            "No access code. The record still syncs.",
        ]
    )
    return _csv(rows)


def _locations() -> str:
    rows = [
        [
            "AxC normal location leading code (984$b)",
            "Institution",
            "Campus",
            "Library",
            "FOLIO location (resolved)",
            "Notes",
        ]
    ]
    for rule in LOCATION_RULES:
        matches = [" or ".join(rule.codes)] if rule.codes else []
        matches += [f"starts with {prefix}" for prefix in rule.prefixes]
        rows.append(
            [
                " or ".join(matches),
                LOCATION_INSTITUTION,
                rule.campus,
                rule.library,
                rule.location,
                "Case-insensitive. First match wins.",
            ]
        )
    rows += [
        [
            "(any other code)",
            "",
            "",
            "",
            "tenant location with that code or name",
            "Record fails if the tenant does not know it.",
        ],
        ["(absent)", "", "", "", "", "Record fails: required, no default."],
    ]
    return _csv(rows)


def _readme(generated: list[str]) -> str:
    lines = [
        f"Mapping version {VERSION} (`mapping/config.py`). One CSV per mapping.",
        "",
        "Generated from `config.py` by `mapping/csv_export.py`, so do not edit them",
        "by hand:",
        "",
        *[f"- `{name}`" for name in generated],
    ]
    return "\n".join(lines) + "\n"


def render() -> dict[str, str]:
    """Every generated file in the folder, by name, including the README."""
    tables = {
        "material-type.csv": _material_type(),
        "item-status.csv": _item_status(),
        "loan-type.csv": _loan_type(),
        "statistical-code.csv": _statistical_code(),
        "locations.csv": _locations(),
    }
    return {**tables, "README.md": _readme(list(tables))}


def write(directory: Path = DEFAULT_DIRECTORY) -> list[Path]:
    """Write :func:`render` into ``directory``, returning the paths written."""
    written = []
    for name, content in render().items():
        path = directory / name
        path.write_text(content)
        written.append(path)
    return written


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_DIRECTORY
    for path in write(target):
        print(path)
