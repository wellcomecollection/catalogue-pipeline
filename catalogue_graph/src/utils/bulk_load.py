from datetime import datetime, timedelta
from pathlib import PurePosixPath

from pydantic import BaseModel, model_validator, parse_obj_as

import config
from utils.types import (
    EntityType,
    TransformerType,
)

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


def get_file_path(
    transformer_type: TransformerType,
    entity_type: EntityType,
    pipeline_date: str,
    window: IncrementalWindow | None = None,
) -> str:
    """Return the file path (object key) of a bulk load file."""
    file_name = f"{transformer_type}__{entity_type}.csv"

    parts: list[str] = [pipeline_date]
    if window is not None:
        parts += ["windows", window.to_formatted_string()]

    file_path = PurePosixPath(*parts) / file_name
    return str(file_path)


def get_s3_uri(
    transformer_type: TransformerType,
    entity_type: EntityType,
    pipeline_date: str,
    window: IncrementalWindow | None = None,
) -> str:
    """Return the S3 URI of a bulk load file."""
    file_path = get_file_path(transformer_type, entity_type, pipeline_date, window)
    return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{config.BULK_LOADER_S3_PREFIX}/{file_path}"
