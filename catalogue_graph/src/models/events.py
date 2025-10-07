import argparse
from typing import Self

from pydantic import BaseModel

import utils.bulk_load as bulk_load
from utils.bulk_load import IncrementalWindow
from utils.types import EntityType, StreamDestination, TransformerType

DEFAULT_INSERT_ERROR_THRESHOLD = 1 / 10000


class BasePipelineEvent(BaseModel):
    pipeline_date: str
    window: IncrementalWindow | None = None
    pit_id: str | None = None

    @classmethod
    def from_argparser(cls, args: argparse.Namespace) -> Self:
        window = None
        if args.window_start is not None or args.window_end is not None:
            window = IncrementalWindow(
                start_time=args.window_start, end_time=args.window_end
            )

        return cls(**args.__dict__, window=window)


class GraphPipelineEvent(BasePipelineEvent):
    transformer_type: TransformerType
    entity_type: EntityType

    def get_bulk_load_s3_uri(self) -> str:
        return bulk_load.get_s3_uri(
            self.transformer_type, self.entity_type, self.pipeline_date, self.window
        )

    def get_bulk_load_file_path(self) -> str:
        return bulk_load.get_file_path(
            self.transformer_type, self.entity_type, self.pipeline_date, self.window
        )


class ExtractorEvent(GraphPipelineEvent):
    stream_destination: StreamDestination = "s3"
    sample_size: int | None = None


class BulkLoaderEvent(GraphPipelineEvent):
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD


class BulkLoadPollerEvent(BaseModel):
    load_id: str
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD


class GraphRemoverEvent(GraphPipelineEvent):
    override_safety_check: bool = False
