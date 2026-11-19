from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel, Field, FilePath, PositiveInt, model_validator


class AppConfig(BaseModel):
    index_sources: list[str] = Field(min_length=2, max_length=2)
    filter_query: dict[str, Any] | None = None
    filter_queries: dict[str, dict[str, Any]] = {}
    ids_file: FilePath | None = None
    ids_format: str | None = None
    ignore_fields: list[str] = []
    ignore_list_order: list[str] = []
    normaliser: FilePath | None = None
    field_depth: PositiveInt = 2
    sample_size: PositiveInt = 10
    loading_chunk_size: PositiveInt = 100_000
    hash_bucket_count: PositiveInt = 6
    workers: PositiveInt = 1
    namespace: str
    output_dir: str = "data"

    @model_validator(mode="after")
    def check_related_settings(self) -> AppConfig:
        unknown = sorted(set(self.filter_queries) - set(self.index_sources))
        if unknown:
            raise ValueError(f"filter_queries keys {unknown} do not match any index source.")
        if self.ids_format and self.ids_file is None:
            raise ValueError("ids_format requires ids_file")
        return self

    def effective_filter_query(self, source_id: str) -> dict[str, Any] | None:
        """Resolve the fetch query for one index source.

        A per-source entry in filter_queries takes precedence over the shared
        filter_query. An ids_file constraint applies to both sources and is
        ANDed with whichever filter applies.
        """
        per_source = self.filter_queries.get(source_id)
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


def load_config(path: str | Path) -> AppConfig:
    """Load an analysis config, with the namespace defaulting to the name of the file."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"Config file not found: {path}")
    raw = yaml.safe_load(path.read_text()) or {}

    # A key with nothing under it loads as None and takes its default.
    values = {key: value for key, value in raw.items() if value is not None}
    values.setdefault("namespace", path.stem)
    # Relative file paths resolve against the config file's directory.
    for key in ("ids_file", "normaliser"):
        if key in values:
            values[key] = (path.parent / values[key]).resolve()
    return AppConfig(**values)


def ensure_dirs(base_output: Path, namespace: str) -> dict[str, Path]:
    root = base_output / namespace
    dirs = {name: root / name for name in ("raw", "parquet", "diffs")}
    for directory in dirs.values():
        directory.mkdir(parents=True, exist_ok=True)
    return dirs
