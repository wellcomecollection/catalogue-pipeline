"""Fixtures for the Elasticsearch versioning tests: a local cluster and a prod-shaped index."""

import json
import subprocess
import time
from collections.abc import Generator
from pathlib import Path

import pytest
import requests
from elasticsearch import ApiError, Elasticsearch

CATALOGUE_GRAPH_DIR = Path(__file__).resolve().parent.parent.parent
REPO_DIR = CATALOGUE_GRAPH_DIR.parent
COMPOSE_FILE = "elasticsearch.docker-compose.yml"

ES_URL = "http://localhost:9200"
INDEX = "works-indexed-versiontest"

# The config the live works-indexed-2026-08-20 is built from. Update these together with
# pipeline/terraform/2025-10-02/main.tf when the served index moves on.
MAPPINGS_FILE = REPO_DIR / "index_config/mappings.works_indexed.2026-08-20.json"
ANALYSIS_FILE = REPO_DIR / "index_config/analysis.works_indexed.2026-08-20.json"
WORKS_FIXTURE = (
    CATALOGUE_GRAPH_DIR / "tests/fixtures/ingestor/works/mock_es_inputs.json"
)

ANALYSIS_KEYS = ("analyzer", "normalizer", "filter", "char_filter", "tokenizer")


@pytest.fixture(scope="session")
def elasticsearch_container() -> Generator[None]:
    """Start Elasticsearch via docker compose for the test session.

    Left running on the way out, so a re-run does not pay the startup cost again. Stop it
    with `docker compose -f elasticsearch.docker-compose.yml down -v`.
    """
    subprocess.run(
        ["docker", "compose", "-f", COMPOSE_FILE, "up", "-d", "elasticsearch"],
        cwd=str(CATALOGUE_GRAPH_DIR),
        check=True,
    )
    for _ in range(60):
        try:
            if requests.get(f"{ES_URL}/_cluster/health", timeout=2).ok:
                break
        except requests.RequestException:
            pass
        time.sleep(2)
    else:
        raise RuntimeError("Elasticsearch container did not become ready in time")
    yield


@pytest.fixture(scope="session")
def es_client(elasticsearch_container: None) -> Elasticsearch:
    return Elasticsearch(ES_URL)


@pytest.fixture(scope="session")
def works_index(es_client: Elasticsearch) -> Generator[str]:
    """Recreate the index from the mappings and analysis the live index is built from."""
    analysis = json.loads(ANALYSIS_FILE.read_text())

    es_client.options(ignore_status=404).indices.delete(index=INDEX)
    es_client.indices.create(
        index=INDEX,
        mappings=json.loads(MAPPINGS_FILE.read_text()),
        settings={
            "index": {
                "analysis": {
                    key: analysis[key] for key in ANALYSIS_KEYS if key in analysis
                },
                "number_of_shards": 1,
                "number_of_replicas": 0,
            }
        },
    )
    yield INDEX
    es_client.options(ignore_status=404).indices.delete(index=INDEX)


@pytest.fixture(scope="session")
def work_document(es_client: Elasticsearch, works_index: str) -> dict:
    """The first fixture work the live mapping accepts.

    Some fixture works carry an anonymised date that does not exist (2014-02-30), which
    the mapping rejects as a date field.
    """
    for document in json.loads(WORKS_FIXTURE.read_text()):
        try:
            es_client.index(
                index=works_index, id="mapping-probe", document=document["_source"]
            )
        except ApiError:
            continue
        es_client.options(ignore_status=404).delete(
            index=works_index, id="mapping-probe"
        )
        return dict(document["_source"])

    raise RuntimeError(f"no work in {WORKS_FIXTURE.name} is valid against the mapping")
