#!/usr/bin/env python3
"""Regenerate the ids file for the 6507 Axiell-CALM comparison config.

Scrolls the round 2 works-identified index (the `new-works-identified` source
in configs/source_configuration.yaml) and writes the canonical ids of every
axiell-guid work to data/6507_axiell_canonical_ids.txt. The scheme lives only
in _source there, so this is a full scroll rather than a filtered query.

Usage, from scripts/es_index_comparison:

    uv run python generate_6507_ids.py
"""

from pathlib import Path

from elasticsearch import helpers

from es_index_comparison.es_client import build_client
from es_index_comparison.source_config import load_source_configuration

HERE = Path(__file__).parent
OUT = HERE / "data" / "6507_axiell_canonical_ids.txt"
SOURCE_ID = "new-works-identified"

source_config = load_source_configuration(HERE / "configs" / "source_configuration.yaml")
source = source_config.resolve_index_sources([SOURCE_ID])[0]
client = build_client(source.cloud_id, source.api_key)

count = 0
with OUT.open("w") as f:
    for hit in helpers.scan(
        client,
        index=source.index,
        _source=["state.sourceIdentifier.identifierType.id"],
        query={"query": {"match_all": {}}},
        size=10_000,
    ):
        scheme = (
            hit["_source"]
            .get("state", {})
            .get("sourceIdentifier", {})
            .get("identifierType", {})
            .get("id")
        )
        if scheme == "axiell-guid":
            f.write(hit["_id"] + "\n")
            count += 1

print(f"wrote {count} ids to {OUT}")
