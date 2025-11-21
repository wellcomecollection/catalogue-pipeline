import argparse
from datetime import datetime, timedelta
from typing import Self

from pydantic import BaseModel, model_validator, parse_obj_as

DEFAULT_WINDOW_MINUTES = 15


class IncrementalWindow(BaseModel):
    start_time: datetime
    end_time: datetime

    @model_validator(mode="before")
    @classmethod
    def calculate_start_time(cls, data: dict) -> dict:
        # If no `start_time` is provided, calculate it by subtracting `DEFAULT_WINDOW_MINUTES` from `end_time`
        if data.get("start_time") is None:
            end_time = parse_obj_as(datetime, data["end_time"])
            data["start_time"] = end_time - timedelta(minutes=DEFAULT_WINDOW_MINUTES)

        return data

    def to_formatted_string(self) -> str:
        start = self.start_time.strftime("%Y%m%dT%H%M")
        end = self.end_time.strftime("%Y%m%dT%H%M")
        return f"{start}-{end}"

    @classmethod
    def from_formatted_string(cls, s: str) -> "IncrementalWindow":
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
