from __future__ import annotations

from pathlib import Path
import typer
from rich.console import Console

from .config import load_config, ensure_dirs
from .es_client import build_client
from .fetch import fetch_both
from .parquet_io import ndjson_gz_to_parquet_shards
from .analysis import compare_indices, show_single_diff

app = typer.Typer(
    help="Deep diff two Elasticsearch indices (read-only) with reproducible artifacts."
)
console = Console()

COMMON_OPTIONS = {
    # Typer infers Path type from annotation; path_type should not be passed as a Path class.
    "config": typer.Option(..., "--config", help="Path to YAML config file."),
    "cloud_id": typer.Option(
        None, "--cloud-id", help="Elastic cloud_id (overrides ES_CLOUD_ID env)."
    ),
    "api_key": typer.Option(None, "--api-key", help="Elastic api_key (overrides ES_API_KEY env)."),
    "namespace": typer.Option(
        None, "--namespace", help="Override namespace output directory name."
    ),
    "output_dir": typer.Option(None, "--output-dir", help="Override base output directory."),
}


def _load_and_prepare(config: Path, cloud_id, api_key, namespace, output_dir):
    overrides = {"cloud_id": cloud_id, "api_key": api_key}
    if namespace:
        overrides["namespace"] = namespace
    if output_dir:
        overrides["output_dir"] = str(output_dir)
    cfg = load_config(config, overrides=overrides)
    eff_ns = cfg.effective_namespace(config)
    base_out = Path(cfg.output_dir)
    dirs = ensure_dirs(base_out, eff_ns)
    return cfg, eff_ns, dirs


@app.command()
def fetch(
    config: Path = COMMON_OPTIONS["config"],
    cloud_id: str | None = COMMON_OPTIONS["cloud_id"],
    api_key: str | None = COMMON_OPTIONS["api_key"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
):
    """Fetch documents from both indices into gzip NDJSON files."""
    cfg, eff_ns, dirs = _load_and_prepare(config, cloud_id, api_key, namespace, output_dir)
    es = build_client(cfg.cloud_id, cfg.api_key)
    fetch_both(es, cfg.indexes, dirs["raw"], cfg.filter_query)
    console.print(f"Fetch complete namespace={eff_ns}")


@app.command()
def convert(
    config: Path = COMMON_OPTIONS["config"],
    cloud_id: str | None = COMMON_OPTIONS["cloud_id"],  # not needed but kept for parity
    api_key: str | None = COMMON_OPTIONS["api_key"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
):
    """Convert fetched NDJSON gzip files into Parquet shards (one folder per index)."""
    cfg, eff_ns, dirs = _load_and_prepare(config, cloud_id, api_key, namespace, output_dir)
    # We don't need ES here, just files
    for idx in cfg.indexes:
        ndjson_file = dirs["raw"] / f"{idx}.ndjson.gz"
        if not ndjson_file.exists():
            console.print(f"Missing raw file {ndjson_file}. Run fetch first.", style="red")
            raise typer.Exit(code=1)
        ndjson_gz_to_parquet_shards(idx, ndjson_file, dirs["parquet"], cfg.loading_chunk_size)
    console.print(f"Convert complete namespace={eff_ns}")


@app.command()
def compare(
    config: Path = COMMON_OPTIONS["config"],
    cloud_id: str | None = COMMON_OPTIONS["cloud_id"],  # not required
    api_key: str | None = COMMON_OPTIONS["api_key"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
):
    """Run diff comparison on existing Parquet shards and write artifacts."""
    cfg, eff_ns, dirs = _load_and_prepare(config, cloud_id, api_key, namespace, output_dir)

    dfs = {}
    for idx in cfg.indexes:
        folder = dirs["parquet"] / idx
        if not folder.exists():
            console.print(f"Parquet folder missing {folder}. Run convert stage.", style="red")
            raise typer.Exit(code=1)
        dfs[idx] = __import__(
            "es_index_comparison.parquet_io", fromlist=["load_parquet_index"]
        ).load_parquet_index(folder)
    compare_indices(
        cfg.indexes[0], cfg.indexes[1], dfs, cfg.ignore_fields, dirs["diffs"], cfg.sample_size
    )
    console.print(f"Compare complete namespace={eff_ns}")


@app.command("run-all")
def run_all(
    config: Path = COMMON_OPTIONS["config"],
    cloud_id: str | None = COMMON_OPTIONS["cloud_id"],
    api_key: str | None = COMMON_OPTIONS["api_key"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
):
    """Execute fetch -> convert -> compare sequentially."""
    cfg, eff_ns, dirs = _load_and_prepare(config, cloud_id, api_key, namespace, output_dir)
    es = build_client(cfg.cloud_id, cfg.api_key)
    fetch_both(es, cfg.indexes, dirs["raw"], cfg.filter_query)
    for idx in cfg.indexes:
        ndjson_file = dirs["raw"] / f"{idx}.ndjson.gz"
        ndjson_gz_to_parquet_shards(idx, ndjson_file, dirs["parquet"], cfg.loading_chunk_size)

    dfs = {
        idx: __import__(
            "es_index_comparison.parquet_io", fromlist=["load_parquet_index"]
        ).load_parquet_index(dirs["parquet"] / idx)
        for idx in cfg.indexes
    }
    compare_indices(
        cfg.indexes[0], cfg.indexes[1], dfs, cfg.ignore_fields, dirs["diffs"], cfg.sample_size
    )
    console.print(f"Run-all complete namespace={eff_ns}")


@app.command("show-diff")
def show_diff(
    id: str = typer.Option(..., "--id", help="Document ID to display diffs for."),
    config: Path = COMMON_OPTIONS["config"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
):
    """Show diffs for a specific document ID (after compare stage)."""
    # We do not need cloud credentials for reading local artifacts.
    cfg = load_config(
        config,
        overrides={"namespace": namespace, "output_dir": str(output_dir) if output_dir else None},
    )
    eff_ns = cfg.effective_namespace(config) if not namespace else namespace
    diff_dir = Path(cfg.output_dir if not output_dir else output_dir) / eff_ns / "diffs"
    if not diff_dir.exists():
        console.print(f"Diff directory {diff_dir} missing. Did you run compare?", style="red")
        raise typer.Exit(code=1)
    rc = show_single_diff(id, diff_dir, cfg.indexes[0], cfg.indexes[1])
    raise typer.Exit(code=rc)


if __name__ == "__main__":  # pragma: no cover
    app()
