import typing
from typing import ClassVar

from pydantic import BaseModel

from clients.metric_reporter import MetricReporter
from ingestor.models.step_events import (
    IngestorStepEvent,
)
from models.events import (
    BulkLoaderEvent,
    GraphPipelineEvent,
    IncrementalGraphRemoverEvent,
)
from models.incremental_window import IncrementalWindow
from models.neptune_bulk_loader import BulkLoadStatusResponse
from utils.aws import pydantic_to_s3_json


class PipelineMetric(BaseModel):
    name: str
    value: typing.Any


class PipelineReport(BaseModel):
    window: IncrementalWindow | None = None
    publish_to_s3: bool = True
    label: ClassVar[str]

    @property
    def s3_uri(self) -> str:
        raise NotImplementedError()

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

    def publish(self) -> None:
        """Write the report to S3 and publish all metrics."""
        if self.publish_to_s3:
            pydantic_to_s3_json(self, self.s3_uri)

        self.put_metrics()


class GraphPipelineReport(PipelineReport, GraphPipelineEvent):
    @property
    def event_key(self) -> str:
        base_key = super().event_key
        return f"report.{base_key}"

    @property
    def s3_uri(self) -> str:
        return self.get_s3_uri("json")

    @property
    def metric_dimensions(self) -> dict:
        return {
            "pipeline_date": self.pipeline_date,
            "transformer_type": self.transformer_type,
            "entity_type": self.entity_type,
        }


class IngestorReport(PipelineReport, IngestorStepEvent):
    @property
    def s3_uri(self) -> str:
        return self.get_s3_uri(f"report.{self.label}", "json")

    @property
    def metric_dimensions(self) -> dict:
        return {
            "pipeline_date": self.pipeline_date,
            "ingestor_type": self.ingestor_type,
            "index_date": self.index_date,
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
            PipelineMetric(
                name="new_count", value=overall.total_records - overall.total_duplicates
            ),
            PipelineMetric(name="parsing_error_count", value=overall.parsing_errors),
            PipelineMetric(
                name="data_type_error_count", value=overall.datatype_mismatch_errors
            ),
            PipelineMetric(name="insert_error_count", value=overall.insert_errors),
        ]


class IncrementalGraphRemoverReport(GraphPipelineReport, IncrementalGraphRemoverEvent):
    label: ClassVar[str] = "incremental_graph_remover"
    deleted_count: int

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="deleted_count", value=self.deleted_count),
        ]


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
