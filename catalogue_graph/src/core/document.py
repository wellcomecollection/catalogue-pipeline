from dataclasses import dataclass
from typing import Any


@dataclass
class Document:
    """A transformed record ready to write: the id it was read under, the id it is
    written under, its JSON-ready body, and an optional version for guarded writes."""

    source_id: str
    target_id: str
    body: dict[str, Any]
    version: int | None = None
