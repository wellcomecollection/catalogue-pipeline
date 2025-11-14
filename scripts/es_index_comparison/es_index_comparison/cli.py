from __future__ import annotations

from pathlib import Path
import typer
from rich.console import Console

from .config import load_config, ensure_dirs
from .fetch import fetch_both
from .parquet_io import ndjson_gz_to_parquet_shards
from .analysis import compare_indices, show_single_diff
from .source_config import load_source_configuration

DEFAULT_CONFIG_RELATIVE = Path("configs/analysis.yaml")
DEFAULT_CONFIG_PATH = (Path(__file__).resolve().parent.parent / DEFAULT_CONFIG_RELATIVE).resolve()

app = typer.Typer(
    help="Deep diff two Elasticsearch indices (read-only) with reproducible artifacts."
)
console = Console()

COMMON_OPTIONS = {
    # Typer infers Path type from annotation; path_type should not be passed as a Path class.
    "config": typer.Option(
        DEFAULT_CONFIG_PATH,
        "--config",
        help=f"Path to YAML config file (default: {DEFAULT_CONFIG_RELATIVE}).",
    ),
    "source_config": typer.Option(
        None,
        "--source-config",
        help="Path to source_configuration.yaml defining clusters and index sources.",
    ),
    "namespace": typer.Option(
        None, "--namespace", help="Override namespace output directory name."
    ),
    "output_dir": typer.Option(None, "--output-dir", help="Override base output directory."),
    "hash_buckets": typer.Option(
        None,
        "--hash-buckets",
        help="Override hash bucket count for Parquet partitioning (default from config).",
    ),
}


def _load_and_prepare(
    config: Path,
    source_config: Path | None,
    namespace,
    output_dir,
    hash_buckets: int | None = None,
):
    overrides = {}
    if namespace:
        overrides["namespace"] = namespace
    if output_dir:
        overrides["output_dir"] = str(output_dir)
    if hash_buckets is not None:
        overrides["hash_bucket_count"] = hash_buckets
    cfg = load_config(config, overrides=overrides)
    namespace_generated = cfg.namespace is None and namespace is None
    eff_ns = cfg.effective_namespace(config)
    base_out = Path(cfg.output_dir)
    dirs = ensure_dirs(base_out, eff_ns)
    if namespace_generated:
        console.print(
            f"[bold cyan]Generated namespace[/]: {eff_ns}  (reuse with --namespace {eff_ns})"
        )
    source_config_path = (
        Path(source_config) if source_config else Path(config).parent / "source_configuration.yaml"
    )
    source_cfg = load_source_configuration(source_config_path)
    resolved_sources = source_cfg.resolve_index_sources(cfg.index_sources)
    return cfg, eff_ns, dirs, resolved_sources


@app.command()
def fetch(
    config: Path = COMMON_OPTIONS["config"],
    source_config: Path | None = COMMON_OPTIONS["source_config"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
    hash_buckets: int | None = COMMON_OPTIONS["hash_buckets"],
):
    """Fetch documents from both indices into gzip NDJSON files."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    fetch_both(sources, dirs["raw"], cfg.filter_query)
    console.print(f"Fetch complete namespace={eff_ns}")


@app.command()
def convert(
    config: Path = COMMON_OPTIONS["config"],
    source_config: Path | None = COMMON_OPTIONS["source_config"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
    hash_buckets: int | None = COMMON_OPTIONS["hash_buckets"],
):
    """Convert fetched NDJSON gzip files into Parquet shards (one folder per index)."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    # We don't need ES here, just files
    for source in sources:
        ndjson_file = dirs["raw"] / f"{source.storage_key}.ndjson.gz"
        if not ndjson_file.exists():
            console.print(f"Missing raw file {ndjson_file}. Run fetch first.", style="red")
            raise typer.Exit(code=1)
        ndjson_gz_to_parquet_shards(
            source.storage_key,
            ndjson_file,
            dirs["parquet"],
            cfg.loading_chunk_size,
            cfg.hash_bucket_count,
        )
    console.print(f"Convert complete namespace={eff_ns}")


@app.command()
def compare(
    config: Path = COMMON_OPTIONS["config"],
    source_config: Path | None = COMMON_OPTIONS["source_config"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
    hash_buckets: int | None = COMMON_OPTIONS["hash_buckets"],
):
    """Run diff comparison on existing Parquet shards and write artifacts."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )

    for source in sources:
        folder = dirs["parquet"] / source.storage_key
        if not folder.exists():
            console.print(f"Parquet folder missing {folder}. Run convert stage.", style="red")
            raise typer.Exit(code=1)
    compare_indices(
        sources[0],
        sources[1],
        dirs["parquet"],
        cfg.ignore_fields,
        dirs["diffs"],
        cfg.sample_size,
    )
    console.print(f"Compare complete namespace={eff_ns}")


@app.command("run-all")
def run_all(
    config: Path = COMMON_OPTIONS["config"],
    source_config: Path | None = COMMON_OPTIONS["source_config"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
    hash_buckets: int | None = COMMON_OPTIONS["hash_buckets"],
):
    """Execute fetch -> convert -> compare sequentially."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    fetch_both(sources, dirs["raw"], cfg.filter_query)
    for source in sources:
        ndjson_file = dirs["raw"] / f"{source.storage_key}.ndjson.gz"
        ndjson_gz_to_parquet_shards(
            source.storage_key,
            ndjson_file,
            dirs["parquet"],
            cfg.loading_chunk_size,
            cfg.hash_bucket_count,
        )

    compare_indices(
        sources[0],
        sources[1],
        dirs["parquet"],
        cfg.ignore_fields,
        dirs["diffs"],
        cfg.sample_size,
    )
    console.print(f"Run-all complete namespace={eff_ns}")


@app.command("show-diff")
def show_diff(
    id: str = typer.Option(..., "--id", help="Document ID to display diffs for."),
    config: Path = COMMON_OPTIONS["config"],
    source_config: Path | None = COMMON_OPTIONS["source_config"],
    namespace: str | None = COMMON_OPTIONS["namespace"],
    output_dir: Path | None = COMMON_OPTIONS["output_dir"],
):
    """Show diffs for a specific document ID (after compare stage)."""
    # We do not need cloud credentials for reading local artifacts.
    cfg = load_config(
        config,
        overrides={"namespace": namespace, "output_dir": str(output_dir) if output_dir else None},
    )
    source_config_path = (
        Path(source_config) if source_config else Path(config).parent / "source_configuration.yaml"
    )
    sources = load_source_configuration(source_config_path).resolve_index_sources(cfg.index_sources)
    eff_ns = cfg.effective_namespace(config) if not namespace else namespace
    diff_dir = Path(cfg.output_dir if not output_dir else output_dir) / eff_ns / "diffs"
    if not diff_dir.exists():
        console.print(f"Diff directory {diff_dir} missing. Did you run compare?", style="red")
        raise typer.Exit(code=1)
    rc = show_single_diff(id, diff_dir, sources[0].label, sources[1].label)
    raise typer.Exit(code=rc)


if __name__ == "__main__":  # pragma: no cover
    app()
