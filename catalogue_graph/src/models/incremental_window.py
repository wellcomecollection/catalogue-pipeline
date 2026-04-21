from __future__ import annotations

import argparse
from datetime import datetime, timedelta
from typing import Self

from pydantic import BaseModel, ConfigDict, model_validator, parse_obj_as
from pydantic.alias_generators import to_camel

from utils.timezone import ensure_datetime_utc

DEFAULT_WINDOW_MINUTES = 15


class IncrementalWindow(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
    )

    start_time: datetime
    end_time: datetime

    @model_validator(mode="before")
    @classmethod
    def calculate_start_time(cls, window: dict) -> dict:
        end_time = window.get("end_time") or window.get("endTime")

        if end_time is None:
            raise ValueError(
                "end_time is required when specifying a time window "
                "(start_time alone is not sufficient)"
            )

        start_time = window.get("start_time") or window.get("startTime")
        if start_time is None:
            end_time = parse_obj_as(datetime, end_time)
            window["start_time"] = end_time - timedelta(minutes=DEFAULT_WINDOW_MINUTES)

        return window

    @model_validator(mode="after")
    def validate_window_order(self) -> Self:
        if self.start_time > self.end_time:
            raise ValueError("start_time must not be after end_time")
        return self

    @property
    def start_time_utc(self) -> datetime:
        return ensure_datetime_utc(self.start_time)

    @property
    def end_time_utc(self) -> datetime:
        return ensure_datetime_utc(self.end_time)

    def to_formatted_string(self) -> str:
        start = self.start_time.strftime("%Y%m%dT%H%M")
        end = self.end_time.strftime("%Y%m%dT%H%M")
        return f"{start}-{end}"

    @classmethod
    def from_formatted_string(cls, s: str) -> IncrementalWindow:
        start_str, end_str = s.split("-")
        start_time = datetime.strptime(start_str, "%Y%m%dT%H%M")
        end_time = datetime.strptime(end_str, "%Y%m%dT%H%M")
        return cls(start_time=start_time, end_time=end_time)

    @classmethod
    def from_argparser(cls, args: argparse.Namespace) -> Self | None:
        window = None
        if args.window_start is not None or args.window_end is not None:
            window = cls(start_time=args.window_start, end_time=args.window_end)

        return window

    def to_iso_string(self) -> str:
        return f"{self.start_time.isoformat()}_{self.end_time.isoformat()}"

    @classmethod
    def from_iso_string(cls, s: str) -> IncrementalWindow:
        start_str, end_str = s.split("_")
        return cls(
            start_time=datetime.fromisoformat(start_str),
            end_time=datetime.fromisoformat(end_str),
        )

    def to_elasticsearch_query(self, field_name: str) -> dict:
        return {
            "range": {
                field_name: {
                    "gte": self.start_time.isoformat(),
                    "lte": self.end_time.isoformat(),
                }
            }
        }
