from datetime import datetime

from pydantic import BaseModel

import config
from utils.types import (
    EntityType,
    TransformerType,
)


class IncrementalWindow(BaseModel):
    start_time: datetime
    end_time: datetime


def get_file_path(
    transformer_type: TransformerType,
    entity_type: EntityType,
    pipeline_date: str,
    window: IncrementalWindow | None = None,
) -> str:
    """Return the file path of a bulk load file."""
    file_name = f"{transformer_type}__{entity_type}.csv"

    window_prefix = ""
    if window is not None:
        start = window.start_time.strftime("%Y%m%dT%H%M")
        end = window.end_time.strftime("%Y%m%dT%H%M")
        window_prefix = f"windows/{start}-{end}/"

    return f"{pipeline_date}/{window_prefix}{file_name}"


def get_s3_uri(
    transformer_type: TransformerType,
    entity_type: EntityType,
    pipeline_date: str,
    window: IncrementalWindow | None = None,
) -> str:
    """Return the S3 URI of a bulk load file."""
    file_path = get_file_path(transformer_type, entity_type, pipeline_date, window)
    return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{config.BULK_LOADER_S3_PREFIX}/{file_path}"
