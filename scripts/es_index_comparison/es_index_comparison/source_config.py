from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import yaml
from elasticsearch import Elasticsearch
from pydantic import BaseModel, ConfigDict, Field, model_validator


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


# Validation errors must not print the configuration, which holds API keys.
HIDE_INPUT = ConfigDict(hide_input_in_errors=True)


class Cluster(BaseModel):
    model_config = HIDE_INPUT

    api_key: str = Field(min_length=1)
    cloud_id: str | None = None
    host: str | None = None

    @model_validator(mode="after")
    def check_address(self) -> Cluster:
        if not (self.cloud_id or self.host):
            raise ValueError("a cluster needs a cloud_id or host")
        return self


class IndexSource(BaseModel):
    cluster: str
    index: str


class SourceConfiguration(BaseModel):
    model_config = HIDE_INPUT

    clusters: dict[str, Cluster]
    index_sources: dict[str, IndexSource]

    @model_validator(mode="after")
    def check_clusters_are_defined(self) -> SourceConfiguration:
        for source_id, source in self.index_sources.items():
            if source.cluster not in self.clusters:
                raise ValueError(
                    f"Index source '{source_id}' refers to undefined cluster '{source.cluster}'."
                )
        return self

    def resolve_index_sources(self, ids: list[str]) -> list[ResolvedIndexSource]:
        resolved = []
        for source_id in ids:
            if source_id not in self.index_sources:
                raise KeyError(f"Index source '{source_id}' not found in source configuration.")
            source = self.index_sources[source_id]
            cluster = self.clusters[source.cluster]
            resolved.append(
                ResolvedIndexSource(
                    id=source_id,
                    index=source.index,
                    cluster_id=source.cluster,
                    api_key=cluster.api_key,
                    cloud_id=cluster.cloud_id,
                    host=cluster.host,
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
    return SourceConfiguration.model_validate(yaml.safe_load(path.read_text()) or {})
