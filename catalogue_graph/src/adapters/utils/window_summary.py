from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, computed_field, field_validator
from pydantic_core import core_schema

from utils.timezone import ensure_datetime_utc


class WindowKey(str):
    """A typed window key derived from start and end timestamps."""

    @classmethod
    def from_dates(cls, start: datetime, end: datetime) -> WindowKey:
        """Create a window key from start and end datetimes."""
        return cls(f"{start.isoformat()}_{end.isoformat()}")

    @classmethod
    def parse(cls, key: str) -> tuple[datetime, datetime]:
        """Parse a window key back into start and end datetimes."""
        start_str, end_str = key.split("_")
        return (datetime.fromisoformat(start_str), datetime.fromisoformat(end_str))

    @classmethod
    def __get_pydantic_core_schema__(
        cls, source_type: Any, handler: Any
    ) -> core_schema.CoreSchema:
        """Implement Pydantic schema to treat WindowKey as a string."""
        return core_schema.no_info_after_validator_function(
            cls,
            core_schema.str_schema(),
        )


class WindowSummary(BaseModel):
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    record_ids: list[str]
    last_error: str | None
    updated_at: datetime
    tags: dict[str, str] | None

    @computed_field  # type: ignore[prop-decorator]
    @property
    def window_key(self) -> WindowKey:
        return WindowKey.from_dates(self.window_start, self.window_end)

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
        # Support dict-like objects (e.g., Mapping types)
        try:
            tags_items = dict(value)
        except (TypeError, ValueError) as e:
            raise ValueError(
                f"tags must be a dict or dict-like object, got {type(value).__name__}: {value!r}"
            ) from e
        return {str(key): str(val) for key, val in tags_items.items()}
