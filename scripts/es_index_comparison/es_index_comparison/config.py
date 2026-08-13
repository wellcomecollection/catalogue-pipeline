from __future__ import annotations

import yaml
import time
from pathlib import Path
from dataclasses import dataclass, field
from typing import Any, Dict, List

DEFAULT_OUTPUT_DIR = "data"


@dataclass
class AppConfig:
    index_sources: List[str]
    filter_query: Dict[str, Any] | None = None
    filter_queries: Dict[str, Dict[str, Any]] | None = None
    ids_file: Path | None = None
    ids_format: str | None = None
    ignore_fields: List[str] = field(default_factory=list)
    sample_size: int = 10
    loading_chunk_size: int = 100_000
    hash_bucket_count: int = 6
    namespace: str | None = None
    output_dir: str = DEFAULT_OUTPUT_DIR

    def effective_namespace(self, config_path: Path | None) -> str:
        if self.namespace:
            return self.namespace
        base = config_path.stem if config_path else "analysis"
        stamp = time.strftime("%Y%m%d-%H%M%S")
        return f"{base}-{stamp}"

    def effective_filter_query(self, source_id: str) -> Dict[str, Any] | None:
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
        ids_clause: Dict[str, Any] = {"ids": {"values": ids}}
        if base is None:
            return {"query": ids_clause}
        # Configs give filters as a full search body ({"query": ...}).
        inner = base.get("query", base)
        return {"query": {"bool": {"filter": [ids_clause, inner]}}}

    def _load_ids(self) -> List[str] | None:
        if self.ids_file is None:
            return None
        ids = []
        with self.ids_file.open("r") as f:
            for line in f:
                value = line.strip()
                if not value or value.startswith("#"):
                    continue
                ids.append(self.ids_format.format(value) if self.ids_format else value)
        if not ids:
            raise ValueError(f"ids_file {self.ids_file} contains no ids")
        return ids

    def validate(self) -> None:
        if len(self.index_sources) != 2:
            raise ValueError("Config 'index_sources' must contain exactly two identifiers.")
        if self.filter_queries:
            unknown = sorted(set(self.filter_queries) - set(self.index_sources))
            if unknown:
                raise ValueError(
                    f"filter_queries keys {unknown} do not match any index source."
                )
        if self.ids_format and self.ids_file is None:
            raise ValueError("ids_format requires ids_file")
        if self.ids_file is not None and not self.ids_file.exists():
            raise ValueError(f"ids_file not found: {self.ids_file}")
        if self.sample_size <= 0:
            raise ValueError("sample_size must be > 0")
        if self.loading_chunk_size <= 0:
            raise ValueError("loading_chunk_size must be > 0")
        if self.hash_bucket_count <= 0:
            raise ValueError("hash_bucket_count must be > 0")


def load_config(path: str | Path, overrides: Dict[str, Any] | None = None) -> AppConfig:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"Config file not found: {path}")
    with p.open("r") as f:
        raw = yaml.safe_load(f) or {}

    if overrides:
        # Shallow override only for documented keys
        for k, v in overrides.items():
            if v is not None:
                raw[k] = v

    raw_ids_file = raw.get("ids_file")
    cfg = AppConfig(
        index_sources=raw.get("index_sources", []),
        filter_query=raw.get("filter_query"),
        filter_queries=raw.get("filter_queries"),
        # Relative ids_file paths resolve against the config file's directory.
        ids_file=(p.parent / raw_ids_file).resolve() if raw_ids_file else None,
        ids_format=raw.get("ids_format"),
        ignore_fields=raw.get("ignore_fields", []) or [],
        sample_size=raw.get("sample_size", 10),
        loading_chunk_size=raw.get("loading_chunk_size", 100_000),
        hash_bucket_count=raw.get("hash_bucket_count", 6),
        namespace=raw.get("namespace"),
        output_dir=raw.get("output_dir", DEFAULT_OUTPUT_DIR),
    )

    cfg.validate()
    return cfg


def ensure_dirs(base_output: Path, namespace: str) -> Dict[str, Path]:
    root = base_output / namespace
    raw_dir = root / "raw"
    parquet_dir = root / "parquet"
    diffs_dir = root / "diffs"
    for d in (root, raw_dir, parquet_dir, diffs_dir):
        d.mkdir(parents=True, exist_ok=True)
    return {"root": root, "raw": raw_dir, "parquet": parquet_dir, "diffs": diffs_dir}
