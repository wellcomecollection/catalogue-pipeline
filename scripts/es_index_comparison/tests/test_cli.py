from __future__ import annotations

import gzip
import json
from types import SimpleNamespace
from typing import Any

from typer.testing import CliRunner

from es_index_comparison import cli


runner = CliRunner()


def _make_dummy_raw(raw_dir, storage_key: str) -> None:
    raw_dir.mkdir(parents=True, exist_ok=True)
    path = raw_dir / f"{storage_key}.ndjson.gz"
    with gzip.open(path, "wt") as fh:
        fh.write(json.dumps({"_id": "doc-1", "_source": {}}))


def test_convert_accepts_multiple_bucket_flags(tmp_path, monkeypatch):
    raw_dir = tmp_path / "raw"
    parquet_dir = tmp_path / "parquet"
    parquet_dir.mkdir()
    storage_key = "source-a"
    _make_dummy_raw(raw_dir, storage_key)

    cfg = SimpleNamespace(hash_bucket_count=8, loading_chunk_size=10)
    dirs = {"raw": raw_dir, "parquet": parquet_dir, "diffs": tmp_path / "diffs"}
    sources = [SimpleNamespace(storage_key=storage_key)]

    monkeypatch.setattr(cli, "_load_and_prepare", lambda *a, **k: (cfg, "ns", dirs, sources))

    captured: dict[str, Any] = {}

    def fake_convert(*args, **kwargs):
        captured["bucket_filter"] = kwargs.get("bucket_filter")
        return []

    monkeypatch.setattr(cli, "ndjson_gz_to_parquet_shards", fake_convert)

    result = runner.invoke(
        cli.app,
        [
            "convert",
            "--bucket",
            "1",
            "-b",
            "3",
        ],
    )

    assert result.exit_code == 0, result.output
    assert captured["bucket_filter"] == {1, 3}


def test_convert_rejects_out_of_range_bucket(tmp_path, monkeypatch):
    cfg = SimpleNamespace(hash_bucket_count=2, loading_chunk_size=10)
    dirs = {"raw": tmp_path / "raw", "parquet": tmp_path / "parquet", "diffs": tmp_path / "diffs"}
    sources = [SimpleNamespace(storage_key="source-a")]
    monkeypatch.setattr(cli, "_load_and_prepare", lambda *a, **k: (cfg, "ns", dirs, sources))

    result = runner.invoke(cli.app, ["convert", "--bucket", "10"])

    assert result.exit_code != 0
    assert "outside the valid range" in result.output
