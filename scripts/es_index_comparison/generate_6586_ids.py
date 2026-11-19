#!/usr/bin/env python3
"""Regenerate the ids file for the 6586 FOLIO-Sierra comparison config.

Scrolls works-identified on the FOLIO pipeline (the `folio-works-identified`
source in configs/source_configuration.yaml) and writes the canonical id of
every folio-instance work to data/6586_folio_canonical_ids.txt. The scheme
lives only in _source there, so this is a full scroll rather than a filtered
query.

Usage, from scripts/es_index_comparison:

    uv run python generate_6586_ids.py
"""

from pathlib import Path

from elasticsearch import helpers

from es_index_comparison.source_config import load_source_configuration

HERE = Path(__file__).parent
OUT = HERE / "data" / "6586_folio_canonical_ids.txt"
SOURCE_ID = "folio-works-identified"

source_config = load_source_configuration(HERE / "configs" / "source_configuration.yaml")
source = source_config.resolve_index_sources([SOURCE_ID])[0]
client = source.cluster.connect()

count = 0
with OUT.open("w") as f:
    for hit in helpers.scan(
        client,
        index=source.index,
        _source=["state.sourceIdentifier.identifierType.id"],
        query={"query": {"match_all": {}}},
        size=10_000,
    ):
        scheme = hit["_source"]["state"]["sourceIdentifier"]["identifierType"]["id"]
        if scheme == "folio-instance":
            f.write(hit["_id"] + "\n")
            count += 1

print(f"wrote {count} ids to {OUT}")
