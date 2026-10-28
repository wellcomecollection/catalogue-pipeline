"""Lists adapter store changesets for a find_work planner, at one pinned snapshot."""

from __future__ import annotations

from collections.abc import Iterator
from functools import cached_property
from typing import Any

from adapters.utils.adapter_store import AdapterStore


class IcebergChangesetSource:
    """A WorkSource whose units of work are changesets, weighted by row count.

    Partitioning by changeset keeps the transformer in changeset mode, so
    tombstones and Axiell deletion facts are delivered exactly as a single
    changeset run delivers them. Every requested changeset is listed, even one
    with no rows left at the snapshot, because its deletion facts still apply.
    """

    def __init__(
        self,
        store: AdapterStore,
        changeset_ids: list[str] | None = None,
        snapshot_id: int | None = None,
    ):
        self.store = store
        self.changeset_ids = (
            list(dict.fromkeys(changeset_ids)) if changeset_ids is not None else None
        )
        self.snapshot_id = (
            snapshot_id if snapshot_id is not None else store.current_snapshot_id()
        )

    @cached_property
    def _stats(self) -> list[dict[str, Any]]:
        stats = self.store.changeset_stats(self.changeset_ids, self.snapshot_id)
        rows = stats.to_pylist()
        unassigned = [row["rows"] for row in rows if row["changeset"] is None]
        if unassigned:
            # Changeset mode cannot reach these rows, so a reindex would skip them
            raise RuntimeError(
                f"{unassigned[0]} adapter rows have no changeset id; a changeset "
                "partitioned reindex cannot reach them"
            )
        return rows

    @cached_property
    def changesets(self) -> list[tuple[str, int]]:
        """(changeset id, row count), in request order or, for a full listing,
        by changeset id."""
        if self.changeset_ids is not None:
            rows_by_changeset = {row["changeset"]: row["rows"] for row in self._stats}
            return [
                (changeset_id, rows_by_changeset.get(changeset_id, 0))
                for changeset_id in self.changeset_ids
            ]
        return sorted((row["changeset"], row["rows"]) for row in self._stats)

    def stream_ids(self) -> Iterator[str]:
        for changeset_id, _ in self.changesets:
            yield changeset_id

    @property
    def row_count(self) -> int:
        return sum(row["rows"] for row in self._stats)

    @property
    def tombstone_count(self) -> int:
        return sum(row["tombstones"] for row in self._stats)
