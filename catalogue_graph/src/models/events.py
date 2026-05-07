import argparse
from pathlib import PurePosixPath
from typing import Self, get_args

from pydantic import BaseModel, model_validator

import config
from models.incremental_window import IncrementalWindow
from models.source_scope import SourceScope
from utils.types import (
    CatalogueTransformerType,
    EntityType,
    Environment,
    FullGraphRemoverType,
    StreamDestination,
    TransformerType,
)

DEFAULT_INSERT_ERROR_THRESHOLD = 1 / 10000


class EventBridgeScheduledEvent(BaseModel):
    time: str


class PipelineIndexDates(BaseModel):
    merged: str | None = None  # merged works
    augmented: str | None = None  # augmented images
    concepts: str | None = None  # final concepts
    works: str | None = None  # final works
    images: str | None = None  # final images


class PipelinePitIds(BaseModel):
    merged: str | None = None
    augmented: str | None = None


class BasePipelineEvent(SourceScope):
    pipeline_date: str
    pit_ids: PipelinePitIds = PipelinePitIds()
    index_dates: PipelineIndexDates = PipelineIndexDates()
    environment: Environment = "prod"

    @classmethod
    def from_argparser(cls, args: argparse.Namespace) -> Self:
        window = None
        if getattr(args, "window_start", None) and getattr(args, "window_end", None):
            window = IncrementalWindow.from_argparser(args)

        index_dates = PipelineIndexDates(
            merged=getattr(args, "index_date_merged", None),
            augmented=getattr(args, "index_date_augmented", None),
        )
        pit_ids = PipelinePitIds(
            merged=getattr(args, "pit_id_merged", None),
            augmented=getattr(args, "pit_id_augmented", None),
        )
        return cls(
            **args.__dict__, window=window, index_dates=index_dates, pit_ids=pit_ids
        )


class GraphPipelineEvent(BasePipelineEvent):
    transformer_type: TransformerType
    entity_type: EntityType

    @model_validator(mode="after")
    def validate_incremental_transformer(self) -> Self:
        catalogue_transformers = get_args(CatalogueTransformerType)
        is_catalogue_transformer = self.transformer_type in catalogue_transformers

        if self.window and not is_catalogue_transformer:
            raise ValueError(
                f"The {self.transformer_type} transformer does not support incremental mode. "
                "Only catalogue transformers support incremental (window-based) processing."
            )
        if self.ids and self.transformer_type != "catalogue_works":
            raise ValueError(
                "ID-based processing is only supported by the `catalogue_works` transformer."
            )

        return self

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
        if self.ids:
            parts += ["by_id", self.ids_path_segment]

        return parts

    def get_file_path(self, file_format: str = "csv", folder: str | None = None) -> str:
        parts = self.file_path_parts
        if folder:
            parts.append(folder)

        return f"{PurePosixPath(*parts)}/{self.event_key}.{file_format}"

    def get_s3_uri(self, file_format: str = "csv", folder: str | None = None) -> str:
        file_path = self.get_file_path(file_format, folder)
        bucket = config.CATALOGUE_GRAPH_S3_BUCKETS[self.environment]
        return f"s3://{bucket}/{file_path}"


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
    environment: Environment = "prod"


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
