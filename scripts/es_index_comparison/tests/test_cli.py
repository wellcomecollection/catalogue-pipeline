from __future__ import annotations

from typer.testing import CliRunner

from es_index_comparison import cli
from es_index_comparison.config import AppConfig

runner = CliRunner()


def use_config(tmp_path, monkeypatch) -> None:
    cfg = AppConfig(index_sources=["a", "b"], namespace="ns")
    dirs = {"docs": tmp_path / "docs", "diffs": tmp_path / "diffs"}
    sources = ["source-a", "source-b"]
    monkeypatch.setattr(cli, "_load_and_prepare", lambda *a, **k: (cfg, dirs, sources))


def test_compare_accepts_multiple_bucket_flags(tmp_path, monkeypatch):
    use_config(tmp_path, monkeypatch)
    captured = {}
    monkeypatch.setattr(cli, "compare_indices", lambda *args: captured.update(args=args))

    result = runner.invoke(cli.app, ["compare", "--bucket", "1", "-b", "3"])

    assert result.exit_code == 0, result.output
    assert captured["args"][-1] == {1, 3}
