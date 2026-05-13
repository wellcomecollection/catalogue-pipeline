"""Shared adapter event models for step inputs and outputs."""

from pydantic import BaseModel, Field


class BaseAdapterEvent(BaseModel):
    """Shared base for adapter step events.

    Provides a required job_id for run tracking without assuming windowed processing.
    Adapters that need windows can additionally inherit from WindowEvent.
    """

    job_id: str
    adapter_type: str
    """Adapter sub-type used for routing/dispatch (e.g. 'axiell', 'folio', 'ebsco')."""


class BaseLoaderResponse(BaseModel):
    """Shared base for loader step responses."""

    changeset_ids: list[str] = Field(default_factory=list)
    changed_record_count: int
    job_id: str
