from __future__ import annotations

import hashlib
from typing import Literal

from pydantic import BaseModel, model_validator

import config
from models.incremental_window import IncrementalWindow
from utils.types import NonEmptyString


class SourceScope(BaseModel):
    """Defines the scope of source data for a pipeline step.

    Controls which documents are fetched from the Elasticsearch source
    index. Inherited by pipeline events to scope every step's input.

    Modes (mutually exclusive):
      - ids: process specific documents by identifier.
      - window: process documents modified within a time range (incremental run).
      - (neither): process all documents (full run).
    """

    ids: list[NonEmptyString] | None = None
    window: IncrementalWindow | None = None

    @model_validator(mode="after")
    def validate_mode(self) -> SourceScope:
        if self.ids == []:
            raise ValueError("ids must not be an empty list")
        if self.ids and self.window:
            raise ValueError("Cannot specify both ids and a time window")

        return self

    @property
    def mode_label(self) -> Literal["ids", "window", "full"]:
        if self.ids:
            return "ids"
        if self.window:
            return "window"
        return "full"

    def to_elasticsearch_query(
        self, range_filter_field_name: str, query: dict | None = None
    ) -> dict:
        query_clauses = []
        if query:
            query_clauses.append(query)
        if self.window:
            range_filter = self.window.to_elasticsearch_query(range_filter_field_name)
            query_clauses.append(range_filter)
        if self.ids:
            id_filter = {"ids": {"values": self.ids}}
            query_clauses.append(id_filter)

        if len(query_clauses) == 0:
            return {"match_all": {}}
        if len(query_clauses) == 1:
            return query_clauses[0]

        return {"bool": {"must": query_clauses}}

    @property
    def slice_count(self) -> int:
        if self.ids:
            return 1

        return config.ES_SOURCE_SLICE_COUNT

    @property
    def ids_path_segment(self) -> str:
        """
        Return a compact, path-safe representation of the selected IDs.
        Short ID lists are joined directly, longer lists are hashed.
        """
        if not self.ids:
            raise ValueError("ids_path_segment is only valid in `id` mode")

        joined_ids = "_".join(sorted(self.ids))
        if len(self.ids) <= 5:
            return joined_ids

        return hashlib.sha256(joined_ids.encode()).hexdigest()[:12]
