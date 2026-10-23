"""Lists adapter store changesets for a find_work planner, at one pinned snapshot."""

from __future__ import annotations

from collections.abc import Iterator
from functools import cached_property

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
    def _counts(self) -> dict[str, tuple[int, int]]:
        return self.store.count_rows_by_changeset(self.changeset_ids, self.snapshot_id)

    @cached_property
    def changesets(self) -> list[tuple[str, int]]:
        """(changeset id, row count), in request order or sorted for a full scan."""
        keys = (
            self.changeset_ids
            if self.changeset_ids is not None
            else sorted(self._counts)
        )
        return [(key, self._counts.get(key, (0, 0))[0]) for key in keys]

    def stream_ids(self) -> Iterator[str]:
        for changeset_id, _ in self.changesets:
            yield changeset_id

    @property
    def row_count(self) -> int:
        return sum(rows for rows, _ in self._counts.values())

    @property
    def tombstone_count(self) -> int:
        return sum(tombstones for _, tombstones in self._counts.values())
