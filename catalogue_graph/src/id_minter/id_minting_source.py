"""ElasticSource subclass that fetches documents by their source identifiers."""

from __future__ import annotations

from elasticsearch import Elasticsearch

from core.source import ElasticSource


class IdMintingSource(ElasticSource):
    """Fetches work documents from a works-source index by document ID.

    Document IDs in the works-source index are composite source identifier
    strings of the form ``OntologyType[identifierType/value]``, e.g.
    ``Work[sierra-system-number/b1000001]``.
    """

    def __init__(
        self,
        es_client: Elasticsearch,
        index_name: str,
        source_identifiers: list[str],
    ):
        super().__init__(
            es_client=es_client,
            index_name=index_name,
            query={"ids": {"values": source_identifiers}},
        )
