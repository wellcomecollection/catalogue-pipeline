import argparse
from datetime import UTC, datetime
from pathlib import PurePosixPath
from typing import Self, get_args

from pydantic import BaseModel, Field, field_validator, model_validator

import config
from models.incremental_window import IncrementalWindow
from models.pipeline_scope import GraphPipelineScope, PipelineIndexDates
from models.source_scope import SourceScope
from utils.types import (
    CatalogueTransformerType,
    EntityType,
    FullGraphRemoverType,
    StreamDestination,
    TransformerType,
)

DEFAULT_INSERT_ERROR_THRESHOLD = 1 / 10000


class ScheduledEvent(BaseModel):
    time: datetime = Field(default_factory=lambda: datetime.now(tz=UTC))


class PipelinePitIds(BaseModel):
    merged: str | None = None
    augmented: str | None = None


class BasePipelineEvent(SourceScope, GraphPipelineScope):
    pit_ids: PipelinePitIds = PipelinePitIds()

    @field_validator("pit_ids", mode="before")
    @classmethod
    def _coerce_pit_ids(cls, v: object) -> object:
        return v if v is not None else PipelinePitIds()

    @classmethod
    def from_argparser(cls, args: argparse.Namespace) -> Self:
        window = None
        if hasattr(args, "window_start") and hasattr(args, "window_end"):
            window = IncrementalWindow.from_argparser(args)

        index_dates = PipelineIndexDates(
            initial=getattr(args, "index_date_initial", None),
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

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        raise NotImplementedError()

    @property
    def s3_prefix_parts(self) -> list[str]:
        """Build the S3 path prefix for this run's output files.

        All services share the same top-level layout:

            graph-{graph_date}/pipeline-{pipeline_date}/{service_prefix(es)}/{scope}

        where:
            - ``graph_date`` identifies the Neptune graph cluster (temporarily defaults to ``prod``)
            - ``pipeline_date`` identifies the Elasticsearch pipeline cluster
            - service-specific segment(s) are provided by ``s3_service_prefix_parts``
            - ``scope`` reflects the pipeline run mode:
                - ``windows/{window}`` for incremental (window-based) runs
                - ``by_id/{ids}`` for ID-based runs
                - ``full`` for a complete reindex
        """
        parts: list[str] = []

        parts += [
            f"graph-{self.graph_date or 'prod'}",
            f"pipeline-{self.pipeline_date}",
        ]

        parts += self.s3_service_prefix_parts

        if self.window is not None:
            parts += ["windows", self.window.to_formatted_string()]
        elif self.ids:
            parts += ["by_id", self.ids_path_segment]
        else:
            parts.append("full")

        return parts


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
    def event_key(self) -> str:
        return f"{self.transformer_type}__{self.entity_type}"

    def get_file_path(self, file_format: str = "csv", folder: str | None = None) -> str:
        parts = self.s3_prefix_parts
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
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.BULK_LOADER_S3_PREFIX]


class BulkLoaderEvent(GraphPipelineEvent):
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.BULK_LOADER_S3_PREFIX]


class BulkLoadPollerEvent(BaseModel):
    load_id: str
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD
    graph_date: str


class GraphRemoverEvent(GraphPipelineEvent):
    force_pass: bool = False


class FullGraphRemoverEvent(GraphRemoverEvent):
    transformer_type: FullGraphRemoverType

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.GRAPH_REMOVER_S3_PREFIX]


class IncrementalGraphRemoverEvent(GraphRemoverEvent):
    transformer_type: CatalogueTransformerType

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.INCREMENTAL_GRAPH_REMOVER_S3_PREFIX]
