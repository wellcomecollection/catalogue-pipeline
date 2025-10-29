from __future__ import annotations

import os
import yaml
import time
from pathlib import Path
from dataclasses import dataclass, field
from typing import Any, Dict, List

DEFAULT_OUTPUT_DIR = "data"


@dataclass
class AppConfig:
    indexes: List[str]
    filter_query: Dict[str, Any] | None = None
    ignore_fields: List[str] = field(default_factory=list)
    sample_size: int = 10
    loading_chunk_size: int = 100_000
    namespace: str | None = None
    output_dir: str = DEFAULT_OUTPUT_DIR

    # Derived / runtime only
    cloud_id: str | None = None
    api_key: str | None = None

    def effective_namespace(self, config_path: Path | None) -> str:
        if self.namespace:
            return self.namespace
        base = config_path.stem if config_path else "analysis"
        stamp = time.strftime("%Y%m%d-%H%M%S")
        return f"{base}-{stamp}"

    def validate(self) -> None:
        if len(self.indexes) != 2:
            raise ValueError("Config 'indexes' must contain exactly two index names.")
        if self.sample_size <= 0:
            raise ValueError("sample_size must be > 0")
        if self.loading_chunk_size <= 0:
            raise ValueError("loading_chunk_size must be > 0")


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

    cfg = AppConfig(
        indexes=raw.get("indexes", []),
        filter_query=raw.get("filter_query"),
        ignore_fields=raw.get("ignore_fields", []) or [],
        sample_size=raw.get("sample_size", 10),
        loading_chunk_size=raw.get("loading_chunk_size", 100_000),
        namespace=raw.get("namespace"),
        output_dir=raw.get("output_dir", DEFAULT_OUTPUT_DIR),
    )

    # Env secrets (do not persist)
    cfg.cloud_id = overrides.get("cloud_id") if overrides else None
    if not cfg.cloud_id:
        cfg.cloud_id = os.getenv("ES_CLOUD_ID")
    cfg.api_key = overrides.get("api_key") if overrides else None
    if not cfg.api_key:
        cfg.api_key = os.getenv("ES_API_KEY")

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
