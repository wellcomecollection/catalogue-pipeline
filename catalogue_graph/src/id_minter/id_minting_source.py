"""ElasticSource subclass for fetching works-source documents.

Supports three modes via factory classmethods:
- ``from_identifiers``: fetch specific documents by ID
- ``from_window``: fetch documents by ``indexed_at`` time range
- ``from_match_all``: fetch all documents (full reprocess)
"""

from __future__ import annotations

from datetime import datetime

from elasticsearch import Elasticsearch

from core.source import ElasticSource


def build_indexed_at_range_query(start_time: datetime, end_time: datetime) -> dict:
    """Build an ES range query filtering on the ``indexed_at`` field."""
    return {
        "range": {
            "indexed_at": {
                "gte": start_time.isoformat(),
                "lte": end_time.isoformat(),
            }
        }
    }


class IdMintingSource(ElasticSource):
    """Fetches work documents from a works-source index.

    Use the factory classmethods to create an instance for the desired mode.
    """

    @classmethod
    def from_identifiers(
        cls,
        es_client: Elasticsearch,
        index_name: str,
        source_identifiers: list[str],
    ) -> IdMintingSource:
        """Fetch specific documents by their composite source identifier strings."""
        return cls(
            es_client=es_client,
            index_name=index_name,
            query={"ids": {"values": source_identifiers}},
            slice_count=1,
        )

    @classmethod
    def from_window(
        cls,
        es_client: Elasticsearch,
        index_name: str,
        start_time: datetime,
        end_time: datetime,
    ) -> IdMintingSource:
        """Fetch documents whose ``indexed_at`` falls within [start, end]."""
        return cls(
            es_client=es_client,
            index_name=index_name,
            query=build_indexed_at_range_query(start_time, end_time),
        )

    @classmethod
    def from_match_all(
        cls,
        es_client: Elasticsearch,
        index_name: str,
    ) -> IdMintingSource:
        """Fetch all documents (full reprocess)."""
        return cls(
            es_client=es_client,
            index_name=index_name,
            query={"match_all": {}},
        )
