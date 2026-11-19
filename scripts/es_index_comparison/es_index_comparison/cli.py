from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console

from .analysis import compare_indices, show_single_diff
from .config import ensure_dirs, load_config
from .fetch import fetch_both
from .source_config import load_source_configuration

DEFAULT_CONFIG_PATH = Path(__file__).resolve().parent.parent / "configs" / "analysis.yaml"

app = typer.Typer(
    help="Deep diff two Elasticsearch indices (read-only) with reproducible artifacts.",
    # Local variables can hold API keys.
    pretty_exceptions_show_locals=False,
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
BucketOption = Annotated[
    list[int] | None,
    typer.Option(
        "--bucket", "-b", help="Only process this hash bucket ID (0-indexed). Repeatable."
    ),
]


def _load_and_prepare(config: Path, source_config: Path | None):
    cfg = load_config(config)
    dirs = ensure_dirs(Path(cfg.output_dir), cfg.namespace)
    source_config_path = source_config or config.parent / "source_configuration.yaml"
    sources = load_source_configuration(source_config_path).resolve_index_sources(cfg.index_sources)
    return cfg, dirs, sources


def _fail(message: str) -> typer.Exit:
    console.print(message, style="red")
    return typer.Exit(code=1)


def _fetch(cfg, dirs, sources) -> None:
    queries = {source.id: cfg.fetch_queries(source.id) for source in sources}
    fetch_both(sources, dirs["docs"], queries, cfg.hash_bucket_count)


def _compare(cfg, dirs, sources, bucket: list[int] | None) -> None:
    bucket_filter = set(bucket) if bucket else None
    compare_indices(cfg, sources[0], sources[1], dirs["docs"], dirs["diffs"], bucket_filter)


@app.command()
def fetch(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
):
    """Fetch documents from both indices into hash-bucketed gzip NDJSON files."""
    cfg, dirs, sources = _load_and_prepare(config, source_config)
    _fetch(cfg, dirs, sources)
    console.print(f"Fetch complete namespace={cfg.namespace}")


@app.command()
def compare(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    bucket: BucketOption = None,
):
    """Diff the fetched documents and write the results."""
    cfg, dirs, sources = _load_and_prepare(config, source_config)
    _compare(cfg, dirs, sources, bucket)
    console.print(f"Compare complete namespace={cfg.namespace}")


@app.command("run-all")
def run_all(
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
    bucket: BucketOption = None,
):
    """Execute fetch then compare."""
    cfg, dirs, sources = _load_and_prepare(config, source_config)
    _fetch(cfg, dirs, sources)
    _compare(cfg, dirs, sources, bucket)
    console.print(f"Run-all complete namespace={cfg.namespace}")


@app.command("show-diff")
def show_diff(
    id: Annotated[str, typer.Option("--id", help="Document ID to display diffs for.")],
    config: ConfigOption = DEFAULT_CONFIG_PATH,
    source_config: SourceConfigOption = None,
):
    """Show diffs for a specific document ID (after compare stage)."""
    _, dirs, sources = _load_and_prepare(config, source_config)
    if not (dirs["diffs"] / "diffs.jsonl").exists():
        raise _fail(f"No diffs.jsonl in {dirs['diffs']}. Did you run compare?")
    found = show_single_diff(id, dirs["diffs"], sources[0].label, sources[1].label)
    raise typer.Exit(code=0 if found else 1)


if __name__ == "__main__":  # pragma: no cover
    app()
