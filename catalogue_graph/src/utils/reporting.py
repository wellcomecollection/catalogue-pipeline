from typing import ClassVar, Self

from ingestor.models.step_events import (
    IngestorStepEvent,
)
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


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


class LoaderReport(PipelineReport):
    label: ClassVar[str] = "loader"
    record_count: int
    total_file_size: int


class DeletionReport(PipelineReport):
    label: ClassVar[str] = "deletions"
    deleted_count: int | None
    date: str


class IndexerReport(PipelineReport):
    label: ClassVar[str] = "indexer"
    success_count: int | None = None
