from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel, field_validator

from utils.timezone import ensure_datetime_utc

ALIGNMENT_EPOCH = datetime(1970, 1, 1, tzinfo=UTC)


class WindowSummary(BaseModel):
    window_key: str
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    record_ids: list[str]
    last_error: str | None
    updated_at: datetime
    tags: dict[str, str] | None

    @field_validator("window_start", "window_end", "updated_at", mode="before")
    @classmethod
    def _coerce_datetime(cls, value: Any) -> datetime:
        if isinstance(value, datetime):
            return ensure_datetime_utc(value)
        if isinstance(value, str):
            return ensure_datetime_utc(datetime.fromisoformat(value))
        raise TypeError(f"Unsupported datetime value: {value!r}")

    @field_validator("record_ids", mode="before")
    @classmethod
    def _coerce_record_ids(cls, value: Any) -> list[str]:
        if value is None:
            return []
        if isinstance(value, (list, tuple)):
            return [str(item) for item in value]
        return [str(value)]

    @field_validator("last_error", mode="before")
    @classmethod
    def _coerce_last_error(cls, value: Any) -> str | None:
        return None if value is None else str(value)

    @field_validator("tags", mode="before")
    @classmethod
    def _coerce_tags(cls, value: Any) -> dict[str, str] | None:
        if value is None:
            return None
        if isinstance(value, dict):
            return {str(key): str(val) for key, val in value.items()}
        try:
            tags_items = dict(value)
        except Exception:  # pragma: no cover - defensive fallback
            tags_items = {}
        return {str(key): str(val) for key, val in tags_items.items()}


def _window_key(start: datetime, end: datetime) -> str:
    return f"{start.isoformat()}_{end.isoformat()}"
