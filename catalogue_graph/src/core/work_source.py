"""The interface find_work planners use to list the ids in scope."""

from __future__ import annotations

from collections.abc import Iterator
from typing import Protocol


class WorkSource(Protocol):
    """Lists the record ids a planner partitions, from any backing store."""

    def stream_ids(self) -> Iterator[str]: ...
