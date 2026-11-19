from __future__ import annotations

from typer.testing import CliRunner

from es_index_comparison import cli
from es_index_comparison.config import AppConfig

runner = CliRunner()


def use_config(tmp_path, monkeypatch, hash_bucket_count: int) -> None:
    cfg = AppConfig(index_sources=["a", "b"], namespace="ns", hash_bucket_count=hash_bucket_count)
    dirs = {"docs": tmp_path / "docs", "diffs": tmp_path / "diffs"}
    sources = ["source-a", "source-b"]
    monkeypatch.setattr(cli, "_load_and_prepare", lambda *a, **k: (cfg, dirs, sources))


def test_compare_accepts_multiple_bucket_flags(tmp_path, monkeypatch):
    use_config(tmp_path, monkeypatch, hash_bucket_count=8)
    captured = {}
    monkeypatch.setattr(cli, "compare_indices", lambda *args: captured.update(args=args))

    result = runner.invoke(cli.app, ["compare", "--bucket", "1", "-b", "3"])

    assert result.exit_code == 0, result.output
    assert captured["args"][-1] == {1, 3}


def test_compare_rejects_out_of_range_bucket(tmp_path, monkeypatch):
    use_config(tmp_path, monkeypatch, hash_bucket_count=2)

    result = runner.invoke(cli.app, ["compare", "--bucket", "10"])

    assert result.exit_code != 0
    assert "outside the valid range" in result.output
