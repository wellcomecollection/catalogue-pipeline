from __future__ import annotations

import time
from dataclasses import dataclass, field, fields
from pathlib import Path
from typing import Any

import yaml


@dataclass
class AppConfig:
    index_sources: list[str] = field(default_factory=list)
    filter_query: dict[str, Any] | None = None
    filter_queries: dict[str, dict[str, Any]] | None = None
    ids_file: Path | None = None
    ids_format: str | None = None
    ignore_fields: list[str] = field(default_factory=list)
    ignore_list_order: list[str] = field(default_factory=list)
    sample_size: int = 10
    loading_chunk_size: int = 100_000
    hash_bucket_count: int = 6
    namespace: str | None = None
    output_dir: str = "data"

    def effective_namespace(self, config_path: Path) -> str:
        return self.namespace or f"{config_path.stem}-{time.strftime('%Y%m%d-%H%M%S')}"

    def effective_filter_query(self, source_id: str) -> dict[str, Any] | None:
        """Resolve the fetch query for one index source.

        A per-source entry in filter_queries takes precedence over the shared
        filter_query. An ids_file constraint applies to both sources and is
        ANDed with whichever filter applies.
        """
        per_source = (self.filter_queries or {}).get(source_id)
        base = per_source if per_source is not None else self.filter_query
        ids = self._load_ids()
        if ids is None:
            return base
        ids_clause: dict[str, Any] = {"ids": {"values": ids}}
        if base is None:
            return {"query": ids_clause}
        # Configs give filters as a full search body ({"query": ...}).
        inner = base.get("query", base)
        return {"query": {"bool": {"filter": [ids_clause, inner]}}}

    def _load_ids(self) -> list[str] | None:
        if self.ids_file is None:
            return None
        lines = [line.strip() for line in self.ids_file.read_text().splitlines()]
        ids = [line for line in lines if line and not line.startswith("#")]
        if not ids:
            raise ValueError(f"ids_file {self.ids_file} contains no ids")
        if self.ids_format:
            return [self.ids_format.format(value) for value in ids]
        return ids

    def validate(self) -> None:
        if len(self.index_sources) != 2:
            raise ValueError("Config 'index_sources' must contain exactly two identifiers.")
        unknown = sorted(set(self.filter_queries or {}) - set(self.index_sources))
        if unknown:
            raise ValueError(f"filter_queries keys {unknown} do not match any index source.")
        if self.ids_format and self.ids_file is None:
            raise ValueError("ids_format requires ids_file")
        if self.ids_file is not None and not self.ids_file.exists():
            raise ValueError(f"ids_file not found: {self.ids_file}")
        for name in ("sample_size", "loading_chunk_size", "hash_bucket_count"):
            if getattr(self, name) <= 0:
                raise ValueError(f"{name} must be > 0")


def load_config(path: str | Path, overrides: dict[str, Any] | None = None) -> AppConfig:
    """Load an analysis config, with any override that is not None replacing the YAML value."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"Config file not found: {path}")
    raw = yaml.safe_load(path.read_text()) or {}
    raw.update({key: value for key, value in (overrides or {}).items() if value is not None})

    known = {f.name for f in fields(AppConfig)}
    values = {key: value for key, value in raw.items() if key in known and value is not None}
    if "ids_file" in values:
        # Relative ids_file paths resolve against the config file's directory.
        values["ids_file"] = (path.parent / values["ids_file"]).resolve()

    cfg = AppConfig(**values)
    cfg.validate()
    return cfg


def ensure_dirs(base_output: Path, namespace: str) -> dict[str, Path]:
    root = base_output / namespace
    dirs = {name: root / name for name in ("raw", "parquet", "diffs")}
    for directory in dirs.values():
        directory.mkdir(parents=True, exist_ok=True)
    return dirs
