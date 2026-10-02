"""Heal 982 parent links left stale by a parent renumber.

Axiell resolves a child's 982 $b (the parent's object number) at export time,
but renumbering a parent doesn't move the children's datestamps, so harvesting
never re-fetches them. After reconcile commits, rewrite $b on live children of
the changeset's records and tag them with the parent's changeset so they travel
downstream with it.
"""

from __future__ import annotations

import re
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any, cast

import pyarrow as pa
import pyarrow.compute as pc
from lxml import etree
from pyiceberg.expressions import And, EqualTo, In, IsNull, Or

from adapters.utils.adapter_store import AdapterStore

MARC_NS = "http://www.loc.gov/MARC21/slim"
NS = {"m": MARC_NS}
ID_PREFIX = "collect:"
OBJECT_NUMBER_PREFIX = "(AltRefNo)"

_PARSER = etree.XMLParser(resolve_entities=False)


@dataclass(frozen=True)
class _Parent:
    record_id: str
    object_number: str
    changeset: str
    superseded: bool


@dataclass
class PartOfHealResult:
    scanned_rows: int = 0
    candidate_rows: int = 0
    healed_ids: list[str] = field(default_factory=list)
    superseded_parent_ids: list[str] = field(default_factory=list)
    superseded_stale_children: int = 0


def _parse(content: str | None) -> Any | None:
    if not content:
        return None
    try:
        return etree.fromstring(content.encode(), _PARSER)
    except etree.XMLSyntaxError:
        return None  # reconcile already skips and counts unparseable rows


def _object_number(root: Any) -> str | None:
    for subfield in root.iterfind("m:datafield[@tag='035']/m:subfield[@code='a']", NS):
        text = subfield.text or ""
        if text.startswith(OBJECT_NUMBER_PREFIX):
            return text.removeprefix(OBJECT_NUMBER_PREFIX) or None
    return None


def _parents(
    rows: list[dict[str, Any]], superseded_ids: set[str]
) -> dict[str, _Parent]:
    """Live changeset rows with an object number, keyed by priref.

    Any of them may be a parent: some parents export no 983.
    """
    parents: dict[str, _Parent] = {}
    for row in rows:
        if row.get("deleted"):
            continue
        root = _parse(row.get("content"))
        if root is None:
            continue
        object_number = _object_number(root)
        if object_number is None:
            continue
        parents[row["id"].removeprefix(ID_PREFIX)] = _Parent(
            record_id=row["id"],
            object_number=object_number,
            changeset=row["changeset"],
            superseded=row["id"] in superseded_ids,
        )
    return parents


def _patch(content: str, parents: dict[str, _Parent]) -> tuple[str, _Parent] | None:
    """Return the patched content and its parent if the 982 $b is stale."""
    root = _parse(content)
    if root is None:
        return None
    for datafield in root.iterfind("m:datafield[@tag='982']", NS):
        priref = datafield.findtext("m:subfield[@code='a']", namespaces=NS)
        parent = parents.get(priref) if priref else None
        if parent is None:
            continue
        b = datafield.find("m:subfield[@code='b']", NS)
        if b is None:
            b = etree.SubElement(datafield, f"{{{MARC_NS}}}subfield", code="b")
        # Compared as decoded text, so '&' in a number doesn't look stale.
        if b.text == parent.object_number:
            return None
        b.text = parent.object_number
        return etree.tostring(root, encoding="unicode"), parent
    return None


def heal_stale_part_of(
    adapter_store: AdapterStore,
    changeset_rows: list[dict[str, Any]],
    superseded_ids: set[str],
) -> PartOfHealResult:
    """Rewrite stale 982 $b on children of the changeset rows.

    Children of parents whose guid changed are counted, not healed: the
    parent becomes a new work.
    """
    result = PartOfHealResult()
    parents = _parents(changeset_rows, superseded_ids)
    if not parents:
        return result

    # Pinned so the scan reads the same rows the changeset read did.
    snapshot_id = adapter_store.current_snapshot_id()
    # Cheap superset prefilter, so lxml only parses likely children.
    pattern = ">(?:" + "|".join(re.escape(p) for p in sorted(parents)) + ")<"
    live = Or(EqualTo("deleted", False), IsNull("deleted"))
    reader = adapter_store.table.scan(
        row_filter=And(EqualTo("namespace", adapter_store.namespace), live),
        selected_fields=("id", "content", "deleted"),
        snapshot_id=snapshot_id,
    ).to_arrow_batch_reader()

    patched: dict[str, tuple[str, str, _Parent]] = {}
    superseded_parents: set[str] = set()
    try:
        for batch in reader:
            result.scanned_rows += batch.num_rows
            # Null content gives a null match, which filter drops.
            candidates = batch.filter(
                pc.match_substring_regex(pc.field("content"), pattern)
            )
            result.candidate_rows += candidates.num_rows
            for row_id, content in zip(
                cast(list[str], candidates.column("id").to_pylist()),
                cast(list[str], candidates.column("content").to_pylist()),
                strict=True,
            ):
                hit = _patch(content, parents)
                if hit is None:
                    continue
                new_content, parent = hit
                if parent.superseded:
                    superseded_parents.add(parent.record_id)
                    result.superseded_stale_children += 1
                else:
                    patched[row_id] = (content, new_content, parent)
    finally:
        reader.close()
    result.superseded_parent_ids = sorted(superseded_parents)
    if not patched:
        return result

    # Read the latest rows and drop any changed since the scan, so a newer
    # loader write is never overwritten.
    adapter_store.table.refresh()
    current = adapter_store.get_namespace_records(In("id", list(patched)))
    rows_by_changeset: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in current.to_pylist():
        old_content, new_content, parent = patched[row["id"]]
        if row["deleted"] or row["content"] != old_content:
            continue
        row["content"] = new_content
        rows_by_changeset[parent.changeset].append(row)

    for changeset_id, rows in rows_by_changeset.items():
        result.healed_ids += adapter_store.overwrite_records(
            pa.Table.from_pylist(rows, schema=adapter_store.schema), changeset_id
        )
    return result
