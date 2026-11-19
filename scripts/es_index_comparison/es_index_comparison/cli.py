from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console

from .analysis import compare_indices, show_single_diff
from .config import ensure_dirs, load_config
from .fetch import fetch_both
from .parquet_io import ndjson_gz_to_parquet_shards
from .source_config import load_source_configuration

DEFAULT_CONFIG_PATH = Path(__file__).resolve().parent.parent / "configs" / "analysis.yaml"

app = typer.Typer(
    help="Deep diff two Elasticsearch indices (read-only) with reproducible artifacts."
)
console = Console()

ConfigOption = Annotated[Path, typer.Option("--config", help="Path to the analysis YAML config.")]
SourceConfigOption = Annotated[
    Path | None,
    typer.Option(
        "--source-config",
        help="Path to the clusters and index sources file "
        "(default: source_configuration.yaml beside the config).",
    ),
]
NamespaceOption = Annotated[
    str | None, typer.Option("--namespace", help="Override namespace output directory name.")
]
OutputDirOption = Annotated[
    Path | None, typer.Option("--output-dir", help="Override base output directory.")
]
HashBucketsOption = Annotated[
    int | None,
    typer.Option("--hash-buckets", help="Override the config's hash bucket count."),
]
BucketOption = Annotated[
    list[int] | None,
    typer.Option(
        "--bucket", "-b", help="Only process this hash bucket ID (0-indexed). Repeatable."
    ),
]


def _load_and_prepare(
    config: Path,
    source_config: Path | None,
    namespace: str | None,
    output_dir: Path | None,
    hash_buckets: int | None = None,
):
    overrides = {
        "namespace": namespace,
        "output_dir": str(output_dir.resolve()) if output_dir else None,
        "hash_bucket_count": hash_buckets,
    }
    cfg = load_config(config, overrides)
    eff_ns = cfg.effective_namespace(config)
    if cfg.namespace is None:
        console.print(
            f"[bold cyan]Generated namespace[/]: {eff_ns}  (reuse with --namespace {eff_ns})"
        )
    dirs = ensure_dirs(Path(cfg.output_dir), eff_ns)
    source_config_path = source_config or config.parent / "source_configuration.yaml"
    sources = load_source_configuration(source_config_path).resolve_index_sources(cfg.index_sources)
    return cfg, eff_ns, dirs, sources


def _parse_bucket_filter(buckets: list[int] | None, hash_bucket_count: int) -> set[int] | None:
    if not buckets:
        return None
    invalid = sorted(b for b in set(buckets) if not 0 <= b < hash_bucket_count)
    if invalid:
        raise typer.BadParameter(
            f"Bucket IDs {invalid} are outside the valid range 0-{hash_bucket_count - 1}"
        )
    return set(buckets)


def _fail(message: str) -> typer.Exit:
    console.print(message, style="red")
    return typer.Exit(code=1)


def _fetch(cfg, dirs, sources) -> None:
    queries = {source.id: cfg.effective_filter_query(source.id) for source in sources}
    fetch_both(sources, dirs["raw"], queries)


def _convert(cfg, dirs, sources, bucket: list[int] | None) -> None:
    bucket_filter = _parse_bucket_filter(bucket, cfg.hash_bucket_count)
    for source in sources:
        ndjson_file = dirs["raw"] / f"{source.storage_key}.ndjson.gz"
        if not ndjson_file.exists():
            raise _fail(f"Missing raw file {ndjson_file}. Run fetch first.")
        ndjson_gz_to_parquet_shards(
            source.storage_key,
            ndjson_file,
            dirs["parquet"],
            cfg.loading_chunk_size,
            cfg.hash_bucket_count,
            bucket_filter=bucket_filter,
        )


def _compare(cfg, dirs, sources, bucket: list[int] | None) -> None:
    bucket_filter = _parse_bucket_filter(bucket, cfg.hash_bucket_count)
    for source in sources:
        folder = dirs["parquet"] / source.storage_key
        if not folder.exists():
            raise _fail(f"Parquet folder missing {folder}. Run convert stage.")
    compare_indices(
        sources[0],
        sources[1],
        dirs["parquet"],
        cfg.ignore_fields,
        dirs["diffs"],
        cfg.sample_size,
        bucket_filter=bucket_filter,
        ignore_list_order=cfg.ignore_list_order,
        normalise=cfg.load_normaliser(),
        field_depth=cfg.field_depth,
    )


@app.command()
def fetch(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    namespace: NamespaceOption = None,
    output_dir: OutputDirOption = None,
    hash_buckets: HashBucketsOption = None,
):
    """Fetch documents from both indices into gzip NDJSON files."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    _fetch(cfg, dirs, sources)
    console.print(f"Fetch complete namespace={eff_ns}")


@app.command()
def convert(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    namespace: NamespaceOption = None,
    output_dir: OutputDirOption = None,
    hash_buckets: HashBucketsOption = None,
    bucket: BucketOption = None,
):
    """Convert fetched NDJSON gzip files into Parquet shards (one folder per index)."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    _convert(cfg, dirs, sources, bucket)
    console.print(f"Convert complete namespace={eff_ns}")


@app.command()
def compare(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    namespace: NamespaceOption = None,
    output_dir: OutputDirOption = None,
    hash_buckets: HashBucketsOption = None,
    bucket: BucketOption = None,
):
    """Run diff comparison on existing Parquet shards and write artifacts."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    _compare(cfg, dirs, sources, bucket)
    console.print(f"Compare complete namespace={eff_ns}")


@app.command("run-all")
def run_all(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    namespace: NamespaceOption = None,
    output_dir: OutputDirOption = None,
    hash_buckets: HashBucketsOption = None,
    bucket: BucketOption = None,
):
    """Execute fetch -> convert -> compare sequentially."""
    cfg, eff_ns, dirs, sources = _load_and_prepare(
        config, source_config, namespace, output_dir, hash_buckets
    )
    _fetch(cfg, dirs, sources)
    _convert(cfg, dirs, sources, bucket)
    _compare(cfg, dirs, sources, bucket)
    console.print(f"Run-all complete namespace={eff_ns}")


@app.command("show-diff")
def show_diff(
    id: Annotated[str, typer.Option("--id", help="Document ID to display diffs for.")],
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    namespace: NamespaceOption = None,
    output_dir: OutputDirOption = None,
):
    """Show diffs for a specific document ID (after compare stage)."""
    _, _, dirs, sources = _load_and_prepare(config, source_config, namespace, output_dir)
    if not (dirs["diffs"] / "diffs.jsonl").exists():
        raise _fail(f"No diffs.jsonl in {dirs['diffs']}. Did you run compare?")
    found = show_single_diff(id, dirs["diffs"], sources[0].label, sources[1].label)
    raise typer.Exit(code=0 if found else 1)


if __name__ == "__main__":  # pragma: no cover
    app()
