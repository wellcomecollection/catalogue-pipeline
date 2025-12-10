from pydantic import BaseModel


class BaseAdapterEvent(BaseModel):
    """Shared base for adapter step events.

    Provides a required job_id for run tracking without assuming windowed processing.
    Adapters that need windows can additionally inherit from WindowEvent.
    """

    job_id: str
