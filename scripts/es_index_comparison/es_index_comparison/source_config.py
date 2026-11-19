from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import yaml
from elasticsearch import Elasticsearch


@dataclass(frozen=True)
class ResolvedIndexSource:
    id: str
    index: str
    cluster_id: str
    api_key: str
    cloud_id: str | None = None
    host: str | None = None

    @property
    def storage_key(self) -> str:
        """Filesystem-safe identifier used for artifact directories."""
        return re.sub(r"[^A-Za-z0-9._-]+", "_", self.id)

    @property
    def label(self) -> str:
        return f"{self.id} ({self.cluster_id}/{self.index})"

    def connect(self) -> Elasticsearch:
        if self.host:
            return Elasticsearch(self.host, api_key=self.api_key)
        return Elasticsearch(cloud_id=self.cloud_id, api_key=self.api_key)


@dataclass(frozen=True)
class SourceConfiguration:
    clusters: dict[str, dict]
    index_sources: dict[str, dict]

    def resolve_index_sources(self, ids: list[str]) -> list[ResolvedIndexSource]:
        resolved = []
        for source_id in ids:
            if source_id not in self.index_sources:
                raise KeyError(f"Index source '{source_id}' not found in source configuration.")
            source = self.index_sources[source_id]
            cluster = self.clusters[source["cluster"]]
            resolved.append(
                ResolvedIndexSource(
                    id=source_id,
                    index=source["index"],
                    cluster_id=source["cluster"],
                    api_key=cluster["api_key"],
                    cloud_id=cluster.get("cloud_id"),
                    host=cluster.get("host"),
                )
            )
        return resolved


def load_source_configuration(path: str | Path) -> SourceConfiguration:
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(
            f"Source configuration file not found: {path}. "
            "Create it from configs/source_configuration.example.yaml."
        )
    raw = yaml.safe_load(path.read_text()) or {}
    clusters = raw.get("clusters") or {}
    index_sources = raw.get("index_sources") or {}

    for cluster_id, cluster in clusters.items():
        if not cluster.get("api_key") or not (cluster.get("cloud_id") or cluster.get("host")):
            raise ValueError(
                f"Cluster '{cluster_id}' must provide an api_key and either cloud_id or host."
            )
    for source_id, source in index_sources.items():
        if not source.get("cluster") or not source.get("index"):
            raise ValueError(f"Index source '{source_id}' must specify both 'cluster' and 'index'.")
        if source["cluster"] not in clusters:
            raise ValueError(
                f"Index source '{source_id}' refers to undefined cluster '{source['cluster']}'."
            )

    return SourceConfiguration(clusters=clusters, index_sources=index_sources)
