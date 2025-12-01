from __future__ import annotations

from datetime import UTC, datetime
from typing import Any, TypedDict

ALIGNMENT_EPOCH = datetime(1970, 1, 1, tzinfo=UTC)


class WindowSummary(TypedDict):
    window_key: str
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    record_ids: list[str]
    last_error: str | None
    updated_at: datetime
    tags: dict[str, str] | None


def _ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def _window_key(start: datetime, end: datetime) -> str:
    return f"{start.isoformat()}_{end.isoformat()}"


def _coerce_window_summary(row: dict[str, Any]) -> WindowSummary:
    window_start_value = row["window_start"]
    window_end_value = row["window_end"]
    updated_at_value = row.get("updated_at")

    def _coerce_datetime(value: Any) -> datetime:
        if isinstance(value, datetime):
            return _ensure_utc(value)
        if isinstance(value, str):
            return _ensure_utc(datetime.fromisoformat(value))
        raise TypeError(f"Unsupported datetime value: {value!r}")

    window_start = _coerce_datetime(window_start_value)
    window_end = _coerce_datetime(window_end_value)
    updated_at = (
        _coerce_datetime(updated_at_value)
        if updated_at_value is not None
        else window_end
    )
    record_ids_raw = row.get("record_ids")
    if record_ids_raw is None:
        record_ids: list[str] = []
    elif isinstance(record_ids_raw, (list, tuple)):
        record_ids = [str(item) for item in record_ids_raw]
    else:
        record_ids = [str(record_ids_raw)]
    last_error_value = row.get("last_error")
    last_error = None if last_error_value is None else str(last_error_value)
    tags_value = row.get("tags")
    tags: dict[str, str] | None = None
    if tags_value is not None:
        if isinstance(tags_value, dict):
            tags = {str(key): str(value) for key, value in tags_value.items()}
        else:
            try:
                tags_items = dict(tags_value)
            except Exception:  # pragma: no cover - defensive fallback
                tags_items = {}
            tags = {str(key): str(value) for key, value in tags_items.items()}

    return {
        "window_key": str(row["window_key"]),
        "window_start": window_start,
        "window_end": window_end,
        "state": str(row["state"]),
        "attempts": int(row["attempts"]),
        "record_ids": record_ids,
        "last_error": last_error,
        "updated_at": updated_at,
        "tags": tags,
    }
