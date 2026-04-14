from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ValidationError, model_validator

from models.incremental_window import IncrementalWindow
from utils.types import NonEmptyString


class SourceDocumentSelection(BaseModel):
    """Describes which documents to fetch from Elasticsearch.

    Three mutually exclusive modes:

    - **identifiers**: Supply ``source_identifiers`` to fetch specific docs.
    - **window**: Supply an incremental window for a time-range query.
    - **match_all**: Omit both to fetch everything.
    """

    ids: list[NonEmptyString] | None = None
    window: IncrementalWindow | None = None

    @model_validator(mode="after")
    def validate_mode(self) -> SourceDocumentSelection:
        if self.ids and self.window:
            raise ValidationError("Cannot specify both ids and a time window")

        return self

    @property
    def mode_label(self) -> Literal["identifiers", "window", "match_all"]:
        if self.ids is not None:
            return "identifiers"
        if self.window is not None:
            return "window"
        return "match_all"

    def to_elasticsearch_query(self, range_filter_field_name: str) -> dict:
        if self.window:
            return self.window.to_elasticsearch_query(range_filter_field_name)
        if self.ids:
            return {"ids": {"values": self.ids}}

        return {"match_all": {}}
