from pydantic import BaseModel, Field, field_validator


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

    # Every Neptune cluster is dated (or "dev"); see infra/graph/neptune.tf.
    graph_date: str = Field(min_length=1)
    pipeline_date: str
    index_dates: PipelineIndexDates = PipelineIndexDates()

    @field_validator("index_dates", mode="before")
    @classmethod
    def _coerce_index_dates(cls, v: object) -> object:
        return v if v is not None else PipelineIndexDates()
