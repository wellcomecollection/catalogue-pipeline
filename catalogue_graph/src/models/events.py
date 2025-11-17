import argparse
from pathlib import PurePosixPath
from typing import Self

from pydantic import BaseModel

import config
from models.incremental_window import IncrementalWindow
from utils.types import (
    CatalogueTransformerType,
    EntityType,
    FullGraphRemoverType,
    StreamDestination,
    TransformerType,
)

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

    @property
    def s3_prefix(self) -> str:
        raise NotImplementedError()

    @property
    def event_key(self) -> str:
        return f"{self.transformer_type}__{self.entity_type}"

    @property
    def file_path_parts(self) -> list[str]:
        parts: list[str] = [self.s3_prefix, self.pipeline_date]
        if self.window is not None:
            parts += ["windows", self.window.to_formatted_string()]

        return parts

    def get_file_path(self, file_format: str = "csv", folder: str | None = None) -> str:
        parts = self.file_path_parts
        if folder:
            parts.append(folder)

        return f"{PurePosixPath(*parts)}/{self.event_key}.{file_format}"

    def get_s3_uri(self, file_format: str = "csv", folder: str | None = None) -> str:
        file_path = self.get_file_path(file_format, folder)
        return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{file_path}"


class ExtractorEvent(GraphPipelineEvent):
    stream_destination: StreamDestination = "s3"
    sample_size: int | None = None

    @property
    def s3_prefix(self) -> str:
        return config.BULK_LOADER_S3_PREFIX


class BulkLoaderEvent(GraphPipelineEvent):
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD

    @property
    def s3_prefix(self) -> str:
        return config.BULK_LOADER_S3_PREFIX


class BulkLoadPollerEvent(BaseModel):
    load_id: str
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD


class GraphRemoverEvent(GraphPipelineEvent):
    force_pass: bool = False


class FullGraphRemoverEvent(GraphRemoverEvent):
    transformer_type: FullGraphRemoverType

    @property
    def s3_prefix(self) -> str:
        return config.GRAPH_REMOVER_S3_PREFIX


class IncrementalGraphRemoverEvent(GraphRemoverEvent):
    transformer_type: CatalogueTransformerType

    @property
    def s3_prefix(self) -> str:
        return config.INCREMENTAL_GRAPH_REMOVER_S3_PREFIX
