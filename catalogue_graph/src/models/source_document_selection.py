from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, model_validator

import config
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
            raise ValueError("Cannot specify both ids and a time window")

        return self

    @property
    def mode_label(self) -> Literal["identifiers", "window", "match_all"]:
        if self.ids is not None:
            return "identifiers"
        if self.window is not None:
            return "window"
        return "match_all"

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
