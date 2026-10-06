import re
from datetime import date
from typing import Annotated

from pydantic import AfterValidator, BaseModel, field_validator

GRAPH_DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def validate_graph_date(value: str) -> str:
    """Every Neptune cluster is dated (YYYY-MM-DD) or 'dev'; see infra/graph/neptune.tf."""
    if value == "dev":
        return value
    try:
        if GRAPH_DATE_PATTERN.match(value):
            date.fromisoformat(value)
            return value
    except ValueError:
        pass
    raise ValueError(f"graph_date must be a date (YYYY-MM-DD) or 'dev', got {value!r}")


GraphDate = Annotated[str, AfterValidator(validate_graph_date)]


class PipelineIndexDates(BaseModel):
    initial: str | None = None  # initial images (inferrer source)
    merged: str | None = None  # merged works
    augmented: str | None = None  # augmented images
    concepts: str | None = None  # final concepts
    works: str | None = None  # final works
    images: str | None = None  # final images


class GraphPipelineScope(BaseModel):
    """
    Fully defines the data layer for a pipeline run, identifying which
    graph cluster, Elasticsearch cluster, and individual Elasticsearch
    indexes a given execution should read from and write to.
    """

    graph_date: GraphDate
    pipeline_date: str
    index_dates: PipelineIndexDates = PipelineIndexDates()

    @field_validator("index_dates", mode="before")
    @classmethod
    def _coerce_index_dates(cls, v: object) -> object:
        return v if v is not None else PipelineIndexDates()
