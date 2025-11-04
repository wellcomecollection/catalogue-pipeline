import typing
from typing import ClassVar, Self

from clients.metric_reporter import MetricReporter
from ingestor.models.step_events import (
    IngestorStepEvent,
)
from pydantic import BaseModel

from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


class PipelineMetric(BaseModel):
    name: str
    value: typing.Any


class PipelineReport(IngestorStepEvent):
    label: ClassVar[str]

    @classmethod
    def read(
        cls, event: IngestorStepEvent, ignore_missing: bool = False
    ) -> Self | None:
        s3_uri = event.get_s3_uri(f"report.{cls.label}", "json")
        return pydantic_from_s3_json(cls, s3_uri, ignore_missing=ignore_missing)

    def write(self) -> None:
        s3_uri = self.get_s3_uri(f"report.{self.label}", "json")
        pydantic_to_s3_json(self, s3_uri)
        
        self.put_metrics()
    
    @property
    def metrics(self) -> list[PipelineMetric]:
        raise NotImplementedError()    

    def put_metrics(self) -> None:
        if self.window is None:
            return
    
        dimensions = {
            "ingestor_type": self.ingestor_type,
            "pipeline_date": self.pipeline_date,
            "index_date": self.index_date
        }
    
        reporter = MetricReporter("catalogue_graph_pipeline")
        for metric in self.metrics:
            reporter.put_metric_data(
                metric_name=metric.name,
                value=metric.value,
                dimensions=dimensions,
                timestamp=self.window.start_time
            )    


class LoaderReport(PipelineReport):
    label: ClassVar[str] = "loader"
    record_count: int
    total_file_size: int

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="record_count", value=self.record_count),
            PipelineMetric(name="total_file_size", value=self.total_file_size)
        ]


class DeletionReport(PipelineReport):
    label: ClassVar[str] = "deletions"
    deleted_count: int | None

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="deleted_count", value=self.deleted_count)
        ]


class IndexerReport(PipelineReport):
    label: ClassVar[str] = "indexer"
    success_count: int | None = None

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="success_count", value=self.success_count)
        ]
