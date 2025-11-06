import typing
from typing import ClassVar, Self

from pydantic import BaseModel

from clients.metric_reporter import MetricReporter
from ingestor.models.step_events import (
    IngestorStepEvent,
)
from models.events import BasePipelineEvent, BulkLoaderEvent, GraphPipelineEvent
from models.neptune_bulk_loader import BulkLoadStatusResponse
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


class PipelineMetric(BaseModel):
    name: str
    value: typing.Any


class PipelineReport(BasePipelineEvent):
    label: ClassVar[str]

    @property
    def metrics(self) -> list[PipelineMetric]:
        raise NotImplementedError()

    @property
    def metric_dimensions(self) -> dict:
        raise NotImplementedError()

    def put_metrics(self) -> None:
        if self.window is None:
            return

        reporter = MetricReporter("catalogue_graph_pipeline")
        for metric in self.metrics:
            reporter.put_metric_data(
                metric_name=metric.name,
                value=metric.value,
                dimensions=self.metric_dimensions,
                timestamp=self.window.end_time,
            )


class GraphPipelineReport(PipelineReport, GraphPipelineEvent):
    @property
    def event_key(self) -> str:
        base_key = super().event_key
        return f"report.{base_key}"

    def write(self) -> None:
        s3_uri = self.get_s3_uri("json")
        pydantic_to_s3_json(self, s3_uri)
        self.put_metrics()

    @property
    def metric_dimensions(self) -> dict:
        return {
            "pipeline_date": self.pipeline_date,
            "transformer_type": self.transformer_type,
            "entity_type": self.entity_type,
        }


class BulkLoaderReport(GraphPipelineReport, BulkLoaderEvent):
    label: ClassVar[str] = "bulk_loader"
    status: BulkLoadStatusResponse

    @property
    def metrics(self) -> list[PipelineMetric]:
        overall = self.status.overall_status
        return [
            PipelineMetric(name="record_count", value=overall.total_records),
            PipelineMetric(name="duplicate_count", value=overall.total_duplicates),
            PipelineMetric(name="parsing_error_count", value=overall.parsing_errors),
            PipelineMetric(
                name="data_type_error_count", value=overall.datatype_mismatch_errors
            ),
            PipelineMetric(name="insert_error_count", value=overall.insert_errors),
        ]


class IncrementalGraphRemoverReport(GraphPipelineReport, BulkLoaderEvent):
    label: ClassVar[str] = "incremental_graph_remover"
    deleted_count: int

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="deleted_count", value=self.deleted_count),
        ]


class IngestorReport(PipelineReport, IngestorStepEvent):
    @classmethod
    def read(cls, event: IngestorStepEvent) -> Self | None:
        s3_uri = event.get_s3_uri(f"report.{cls.label}", "json")
        return pydantic_from_s3_json(cls, s3_uri)

    def write(self) -> None:
        s3_uri = self.get_s3_uri(f"report.{self.label}", "json")
        pydantic_to_s3_json(self, s3_uri)

        self.put_metrics()

    @property
    def metric_dimensions(self) -> dict:
        return {
            "pipeline_date": self.pipeline_date,
            "ingestor_type": self.ingestor_type,
            "index_date": self.index_date,
        }


class LoaderReport(IngestorReport):
    label: ClassVar[str] = "loader"
    record_count: int
    total_file_size: int

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="record_count", value=self.record_count),
            PipelineMetric(name="total_file_size", value=self.total_file_size),
        ]


class IndexerReport(IngestorReport):
    label: ClassVar[str] = "indexer"
    success_count: int

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [PipelineMetric(name="success_count", value=self.success_count)]


class DeletionReport(IngestorReport):
    label: ClassVar[str] = "deletions"
    deleted_count: int

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [PipelineMetric(name="deleted_count", value=self.deleted_count)]
