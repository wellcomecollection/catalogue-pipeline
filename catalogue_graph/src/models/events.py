import argparse
from datetime import datetime
from typing import Self

from pydantic import BaseModel
from utils.types import EntityType, StreamDestination, TransformerType

DEFAULT_INSERT_ERROR_THRESHOLD = 1 / 10000


class IncrementalWindow(BaseModel):
    start_time: datetime
    end_time: datetime


class GraphPipelineEvent(BaseModel):
    transformer_type: TransformerType
    entity_type: EntityType
    window: IncrementalWindow | None = None
    pipeline_date: str

    @classmethod
    def from_argparser(cls, args: argparse.Namespace) -> Self:
        window = None
        if args.window_start is not None and args.window_end is not None:
            window = IncrementalWindow(
                start_time=args.window_start, end_time=args.window_end
            )

        return cls(**args.__dict__, window=window)


class ExtractorEvent(GraphPipelineEvent):
    stream_destination: StreamDestination
    sample_size: int | None = None


class BulkLoaderEvent(GraphPipelineEvent):
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD


class BulkLoadPollerEvent(BaseModel):
    load_id: str
    pipeline_date: str
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD


class GraphRemoverEvent(GraphPipelineEvent):
    override_safety_check: bool = False
