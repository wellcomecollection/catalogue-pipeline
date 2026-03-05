"""Tests for IdMintingSource."""

from __future__ import annotations

from typing import cast

from elasticsearch import Elasticsearch

from id_minter.id_minting_source import IdMintingSource
from tests.mocks import MockElasticsearchClient


def test_builds_ids_query_from_source_identifiers() -> None:
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
    source_ids = [
        "Work[sierra-system-number/b1000001]",
        "Work[miro-image-number/A0001234]",
    ]

    source = IdMintingSource(
        es_client=es_client,
        index_name="works-source-dev",
        source_identifiers=source_ids,
    )

    assert source.query == {"ids": {"values": source_ids}}
    assert source.index_name == "works-source-dev"


def test_empty_source_identifiers() -> None:
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))

    source = IdMintingSource(
        es_client=es_client,
        index_name="works-source-dev",
        source_identifiers=[],
    )

    assert source.query == {"ids": {"values": []}}
