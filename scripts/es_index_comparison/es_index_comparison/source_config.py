from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List

import yaml


@dataclass(frozen=True)
class ClusterDefinition:
    id: str
    cloud_id: str
    api_key: str


@dataclass(frozen=True)
class IndexSourceDefinition:
    id: str
    cluster_id: str
    index: str


@dataclass(frozen=True)
class ResolvedIndexSource:
    id: str
    index: str
    cluster_id: str
    cloud_id: str
    api_key: str

    @property
    def storage_key(self) -> str:
        """Filesystem-safe identifier used for artifact directories."""
        return re.sub(r"[^A-Za-z0-9._-]+", "_", self.id)

    @property
    def label(self) -> str:
        """Human-readable label including the cluster and index name."""
        return f"{self.id} ({self.cluster_id}/{self.index})"


class SourceConfiguration:
    def __init__(
        self,
        clusters: Dict[str, ClusterDefinition],
        index_sources: Dict[str, IndexSourceDefinition],
    ) -> None:
        self._clusters = clusters
        self._index_sources = index_sources

    def resolve_index_sources(self, ids: List[str]) -> List[ResolvedIndexSource]:
        resolved: List[ResolvedIndexSource] = []
        for source_id in ids:
            if source_id not in self._index_sources:
                raise KeyError(f"Index source '{source_id}' not found in source configuration.")
            index_source = self._index_sources[source_id]
            if index_source.cluster_id not in self._clusters:
                raise KeyError(
                    f"Cluster '{index_source.cluster_id}' referenced by index source '{source_id}' "
                    "is not defined."
                )
            cluster = self._clusters[index_source.cluster_id]
            if not cluster.cloud_id or not cluster.api_key:
                raise ValueError(
                    f"Cluster '{cluster.id}' is missing cloud_id or api_key credentials."
                )
            resolved.append(
                ResolvedIndexSource(
                    id=source_id,
                    index=index_source.index,
                    cluster_id=index_source.cluster_id,
                    cloud_id=cluster.cloud_id,
                    api_key=cluster.api_key,
                )
            )
        return resolved


def load_source_configuration(path: str | Path) -> SourceConfiguration:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(
            f"Source configuration file not found: {p}. "
            "Create it from configs/source_configration.example.yaml."
        )

    with p.open("r") as f:
        raw = yaml.safe_load(f) or {}

    clusters_raw = raw.get("clusters")
    if not isinstance(clusters_raw, dict) or not clusters_raw:
        raise ValueError("source_configuration.yaml must define a non-empty 'clusters' mapping.")

    clusters: Dict[str, ClusterDefinition] = {}
    for cluster_id, cluster_data in clusters_raw.items():
        if not isinstance(cluster_data, dict):
            raise ValueError(f"Cluster '{cluster_id}' entry must be a mapping.")
        cloud_id = cluster_data.get("cloud_id")
        api_key = cluster_data.get("api_key")
        if not cloud_id or not api_key:
            raise ValueError(f"Cluster '{cluster_id}' must provide both cloud_id and api_key.")
        clusters[cluster_id] = ClusterDefinition(id=cluster_id, cloud_id=cloud_id, api_key=api_key)

    index_sources_raw = raw.get("index_sources")
    if not isinstance(index_sources_raw, dict) or not index_sources_raw:
        raise ValueError(
            "source_configuration.yaml must define a non-empty 'index_sources' mapping."
        )

    index_sources: Dict[str, IndexSourceDefinition] = {}
    for source_id, source_data in index_sources_raw.items():
        if not isinstance(source_data, dict):
            raise ValueError(f"Index source '{source_id}' entry must be a mapping.")
        cluster_id = source_data.get("cluster")
        index_name = source_data.get("index")
        if not cluster_id or not index_name:
            raise ValueError(f"Index source '{source_id}' must specify both 'cluster' and 'index'.")
        index_sources[source_id] = IndexSourceDefinition(
            id=source_id,
            cluster_id=cluster_id,
            index=index_name,
        )

    return SourceConfiguration(clusters=clusters, index_sources=index_sources)
