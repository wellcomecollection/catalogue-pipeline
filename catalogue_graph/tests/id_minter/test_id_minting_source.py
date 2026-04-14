"""Tests for IdMintingSource."""

from __future__ import annotations

from datetime import datetime
from typing import cast

from elasticsearch import Elasticsearch

from id_minter.id_minting_source import IdMintingSource
from models.incremental_window import IncrementalWindow
from models.source_document_selection import SourceDocumentSelection
from tests.mocks import MockElasticsearchClient

START_TIME = datetime(2025, 3, 25, 14, 45, 0)
END_TIME = datetime(2025, 3, 25, 15, 0, 0)


def test_builds_range_query_from_time_window() -> None:
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))

    source = IdMintingSource.from_document_selection(
        es_client=es_client,
        index_name="works-source-dev",
        document_selection=SourceDocumentSelection(
            window=IncrementalWindow(start_time=START_TIME, end_time=END_TIME),
        ),
        range_filter_field_name="indexed_at",
    )

    assert source.query == {
        "range": {
            "indexed_at": {
                "gte": START_TIME.isoformat(),
                "lte": END_TIME.isoformat(),
            }
        }
    }
    assert source.index_name == "works-source-dev"


def test_builds_ids_query_from_identifiers() -> None:
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))

    source = IdMintingSource.from_document_selection(
        es_client=es_client,
        index_name="works-source-dev",
        document_selection=SourceDocumentSelection(
            ids=[
                "Work[sierra-system-number/b1000001]",
                "Work[sierra-system-number/b1000002]",
            ],
        ),
        range_filter_field_name="indexed_at",
    )

    assert source.query == {
        "ids": {
            "values": [
                "Work[sierra-system-number/b1000001]",
                "Work[sierra-system-number/b1000002]",
            ],
        }
    }
    assert source.index_name == "works-source-dev"


def test_builds_match_all_query() -> None:
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))

    source = IdMintingSource.from_document_selection(
        es_client=es_client,
        index_name="works-source-dev",
        document_selection=SourceDocumentSelection(),
        range_filter_field_name="indexed_at",
    )

    assert source.query == {"match_all": {}}
    assert source.index_name == "works-source-dev"
