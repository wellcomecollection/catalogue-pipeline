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


from pathlib import PurePosixPath

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
        start = window.start_time.strftime("%Y%m%dT%H%M")
        end = window.end_time.strftime("%Y%m%dT%H%M")
        parts += ["windows", f"{start}-{end}"]

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
