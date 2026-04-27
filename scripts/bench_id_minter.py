#!/usr/bin/env python3
"""Benchmark the id_minter pipeline at one or more git refs.

Use this to quantify a perf change to the id_minter before opening a PR. For
each git ref you pass, the script checks that ref out into a temporary
``git worktree``, runs ``uv sync --frozen`` for it, then shells out to
itself in *inner mode* so the timed code is loaded from the worktree's
``src/`` and venv (not the active branch). Your working tree is never
touched and worktrees are cleaned up on exit.

A markdown table is printed to stdout suitable for pasting into a PR
description.

Which refs are tested
---------------------
You choose, via ``--refs``. The first ref is the *baseline*; every other
ref is reported as ``Δ%`` relative to it. Any git revspec works
(branches, tags, ``HEAD~1``, SHAs). Typical usage:

- Compare your branch against main::

    python scripts/bench_id_minter.py --refs main HEAD

- Show cumulative gains commit-by-commit::

    python scripts/bench_id_minter.py \\
        --refs main my-branch~1 my-branch

- Reproduce the table on PR #3341::

    python scripts/bench_id_minter.py \\
        --refs main rk/id-minter-mint-batching~1 rk/id-minter-mint-batching \\
        --iterations 5 --include-noop

Workloads
---------
Each ref is exercised against every workload in ``--workloads`` (default:
all four). Workloads are deterministic and synthetic:

- ``all-new``: 1000 works × 6 identifiers, none pre-seeded → all minted.
- ``all-existing``: 1000 works × 6 identifiers, all pre-seeded → pure
  lookup path (0 INSERTs).
- ``mixed``: 1000 works with a 50/40/10 split of new / existing /
  predecessor-inheriting primary identifiers.
- ``large-fanout``: 200 works × 51 identifiers each, all-new (stresses the
  per-call identifier-list size rather than per-call overhead).

What's measured
---------------
Per (ref, workload, iteration), two numbers:

- Wall time of ``IdMintingTransformer.transform(docs)`` via
  ``time.perf_counter``. ES indexing is *not* in the timing — a stub
  bypasses it so we measure the transformer + resolver + MySQL only.
- A SQL-statement count (total + INSERTs) obtained by wrapping the
  resolver's pymysql cursor.

``--include-noop`` adds a second pass per (ref, workload) using a no-op
resolver that fabricates canonical IDs without touching MySQL. This
isolates the non-DB cost of the transformer; if the no-op rows look the
same across refs, any wall-time delta with the real resolver is
attributable to DB interaction rather than transformer plumbing.

Requirements
------------
- ``docker`` + ``docker compose`` (for MySQL)
- ``uv`` on PATH
- ``git`` >= 2.5 (for ``git worktree``)
- ``catalogue_graph/mysql.docker-compose.yml`` — port 3306 must be free.

Output
------
Markdown table on stdout (``--output json`` dumps raw per-iteration data
instead). Worktrees are removed unless ``--keep-worktrees`` is set.

Caveats
-------
The MySQL container runs on ``localhost`` (loopback, ~0.1ms RTT).
Production traffic crosses a VPC to RDS (~1–2ms RTT), so the absolute
benefit of collapsing per-row INSERTs is likely *larger* in production —
but ES indexing (excluded here) will dilute the percentage gain at the
Lambda level.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from collections.abc import Iterable
from pathlib import Path

# Resolve repo root from this script's location (scripts/bench_id_minter.py).
REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOGUE_GRAPH_DIR = REPO_ROOT / "catalogue_graph"
MYSQL_COMPOSE_FILE = CATALOGUE_GRAPH_DIR / "mysql.docker-compose.yml"


# ---------------------------------------------------------------------------
# Outer mode: orchestration (stdlib only)
# ---------------------------------------------------------------------------


def _slug(ref: str) -> str:
    """Make a ref name safe for use in a directory name."""
    return re.sub(r"[^a-zA-Z0-9_.-]", "_", ref)


def _short_sha(ref: str) -> str:
    out = subprocess.run(
        ["git", "rev-parse", "--short", ref],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return out.stdout.strip()


def _ensure_mysql_running() -> None:
    """Start the MySQL container and wait until it accepts connections."""
    print("[bench] Starting MySQL container...", file=sys.stderr)
    subprocess.run(
        ["docker", "compose", "-f", str(MYSQL_COMPOSE_FILE), "up", "-d"],
        cwd=CATALOGUE_GRAPH_DIR,
        check=True,
    )
    # Poll readiness via mysqladmin in the container itself — avoids importing
    # pymysql in outer mode.
    for _ in range(60):
        result = subprocess.run(
            [
                "docker",
                "exec",
                "id-minter-mysql",
                "mysqladmin",
                "ping",
                "-h",
                "localhost",
                "--silent",
            ],
            capture_output=True,
        )
        if result.returncode == 0:
            print("[bench] MySQL ready.", file=sys.stderr)
            return
        time.sleep(1)
    raise RuntimeError("MySQL container did not become ready in 60s")


def _create_worktree(ref: str, parent: Path) -> Path:
    """Add a git worktree for ``ref`` under ``parent``; return its path."""
    target = parent / _slug(ref)
    print(f"[bench] git worktree add {target} {ref}", file=sys.stderr)
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(target), ref],
        cwd=REPO_ROOT,
        check=True,
    )
    return target


def _remove_worktree(path: Path) -> None:
    print(f"[bench] git worktree remove --force {path}", file=sys.stderr)
    subprocess.run(
        ["git", "worktree", "remove", "--force", str(path)],
        cwd=REPO_ROOT,
        check=False,
    )


def _uv_sync(worktree: Path) -> None:
    cg_dir = worktree / "catalogue_graph"
    print(f"[bench] uv sync --frozen in {cg_dir}", file=sys.stderr)
    subprocess.run(
        ["uv", "sync", "--frozen"],
        cwd=cg_dir,
        check=True,
    )


def _run_inner(
    worktree: Path,
    ref: str,
    workload: str,
    iterations: int,
    works_per_batch: int,
    resolver: str,
) -> list[dict]:
    """Invoke this script in --inner-mode inside the worktree's venv."""
    cg_dir = worktree / "catalogue_graph"
    env = os.environ.copy()
    # Ensure inner mode can import id_minter.* from the worktree's source tree
    # (uv run python doesn't honour pytest's pythonpath setting).
    env["PYTHONPATH"] = str(cg_dir / "src")
    cmd = [
        "uv",
        "run",
        "python",
        str(Path(__file__).resolve()),
        "--inner-mode",
        "--ref",
        ref,
        "--workload",
        workload,
        "--iterations",
        str(iterations),
        "--works-per-batch",
        str(works_per_batch),
        "--resolver",
        resolver,
    ]
    proc = subprocess.run(
        cmd,
        cwd=cg_dir,
        env=env,
        check=True,
        capture_output=True,
        text=True,
    )
    # Each line of stdout from inner mode that starts with '{' is a JSON record.
    records: list[dict] = []
    for line in proc.stdout.splitlines():
        line = line.strip()
        if line.startswith("{"):
            records.append(json.loads(line))
    if proc.stderr:
        # Forward the subprocess's progress logs.
        sys.stderr.write(proc.stderr)
    return records


def _aggregate(records: list[dict]) -> dict[tuple[str, str, str], dict]:
    """Group raw per-iteration records by (ref, workload, resolver) and summarise."""
    grouped: dict[tuple[str, str, str], list[dict]] = {}
    for r in records:
        key = (r["ref"], r["workload"], r.get("resolver", "mysql"))
        grouped.setdefault(key, []).append(r)
    summary: dict[tuple[str, str, str], dict] = {}
    for key, rows in grouped.items():
        wall = sorted(r["wall_ms"] for r in rows)
        inserts = [r["sql_inserts"] for r in rows]
        total = [r["sql_total"] for r in rows]
        summary[key] = {
            "n": len(rows),
            "min_ms": wall[0],
            "median_ms": statistics.median(wall),
            "p95_ms": wall[max(0, int(len(wall) * 0.95) - 1)],
            "max_ms": wall[-1],
            "sql_inserts_median": statistics.median(inserts),
            "sql_total_median": statistics.median(total),
        }
    return summary


def _format_markdown(
    refs: list[str],
    workloads: list[str],
    resolvers: list[str],
    summary: dict[tuple[str, str, str], dict],
    short_shas: dict[str, str],
) -> str:
    baseline_ref = refs[0]
    last_ref = refs[-1]
    headers = ["Workload"]
    for ref in refs:
        suffix = f" `{short_shas.get(ref, '')}`"
        headers.append(f"{ref}{suffix} (med ms)")
        if ref != baseline_ref:
            headers.append("Δ%")
    headers.append(f"INSERTs/run ({last_ref})")
    headers.append(f"Total SQL/run ({last_ref})")

    lines = [
        "| " + " | ".join(headers) + " |",
        "|" + "|".join(["---"] * len(headers)) + "|",
    ]
    for resolver in resolvers:
        for workload in workloads:
            label = workload if resolver == "mysql" else f"{workload} (noop)"
            row: list[str] = [label]
            baseline = summary.get((baseline_ref, workload, resolver), {}).get(
                "median_ms"
            )
            for ref in refs:
                entry = summary.get((ref, workload, resolver))
                if entry is None:
                    row.append("—")
                    if ref != baseline_ref:
                        row.append("—")
                    continue
                med = entry["median_ms"]
                row.append(f"{med:.1f}")
                if ref != baseline_ref:
                    if baseline and baseline > 0:
                        delta = (med - baseline) / baseline * 100
                        row.append(f"{delta:+.1f}%")
                    else:
                        row.append("—")
            last_entry = summary.get((last_ref, workload, resolver), {})
            row.append(f"{last_entry.get('sql_inserts_median', '—')}")
            row.append(f"{last_entry.get('sql_total_median', '—')}")
            lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def _format_env_line() -> str:
    return (
        f"_Env: Python {platform.python_version()} on "
        f"{platform.machine()} ({os.cpu_count()} CPUs); "
        f"MySQL via `{MYSQL_COMPOSE_FILE.name}` on localhost._"
    )


def outer_main(args: argparse.Namespace) -> int:
    refs: list[str] = args.refs
    workloads: list[str] = args.workloads
    iterations: int = args.iterations

    short_shas = {ref: _short_sha(ref) for ref in refs}
    resolvers = ["mysql"]
    if args.include_noop:
        resolvers.append("noop")
    if any(r != "noop" for r in resolvers):
        _ensure_mysql_running()

    parent = Path(tempfile.mkdtemp(prefix="bench_id_minter_", dir=REPO_ROOT))
    worktrees: dict[str, Path] = {}
    all_records: list[dict] = []
    try:
        for ref in refs:
            wt = _create_worktree(ref, parent)
            worktrees[ref] = wt
            _uv_sync(wt)
            for resolver in resolvers:
                for workload in workloads:
                    print(
                        f"[bench] Running ref={ref} workload={workload} "
                        f"resolver={resolver} iterations={iterations}",
                        file=sys.stderr,
                    )
                    records = _run_inner(
                        wt,
                        ref,
                        workload,
                        iterations,
                        args.works_per_batch,
                        resolver,
                    )
                    all_records.extend(records)
    finally:
        if not args.keep_worktrees:
            for wt in worktrees.values():
                _remove_worktree(wt)
            shutil.rmtree(parent, ignore_errors=True)
        else:
            print(f"[bench] Keeping worktrees in {parent}", file=sys.stderr)

    if args.output == "json":
        print(
            json.dumps(
                {
                    "refs": refs,
                    "workloads": workloads,
                    "iterations": iterations,
                    "short_shas": short_shas,
                    "records": all_records,
                },
                indent=2,
            )
        )
        return 0

    summary = _aggregate(all_records)
    print(f"# id_minter benchmark — {iterations} iterations/cell\n")
    print(_format_env_line())
    print()
    print(_format_markdown(refs, workloads, resolvers, summary, short_shas))
    print()
    print(
        "_Loopback MySQL understates the multi-row-INSERT win vs production "
        "(RDS in-VPC); expect a larger absolute gain there._"
    )
    return 0


# ---------------------------------------------------------------------------
# Inner mode: runs inside a worktree's catalogue_graph venv. Imports id_minter.
# ---------------------------------------------------------------------------


def _inner_imports() -> tuple:
    """Lazy import so outer mode stays stdlib-only."""
    import pymysql  # type: ignore[import-not-found]
    import pymysql.cursors  # type: ignore[import-not-found]

    from id_minter.config import IdMinterConfig, RDSClientConfig  # noqa: E402
    from id_minter.database import apply_migrations  # noqa: E402
    from id_minter.id_minting_transformer import IdMintingTransformer  # noqa: E402
    from id_minter.resolvers.minting_resolver import MintingResolver  # noqa: E402

    return (
        pymysql,
        IdMinterConfig,
        RDSClientConfig,
        apply_migrations,
        IdMintingTransformer,
        MintingResolver,
    )


def _make_work(idx: int, n_extra: int, kind: str, items_kind: str = "new") -> dict:
    """Build a synthetic source-work doc.

    ``kind`` controls the work's *primary* sourceIdentifier:
      - ``new``: never seeded (will be minted).
      - ``existing``: pre-seeded into the DB (lookup-only path).
      - ``predecessor``: includes a predecessorIdentifier referencing an
        existing seeded record.
    ``items_kind`` independently controls nested item identifiers — set to
    ``existing`` to make the whole work lookup-only.
    """
    primary_value = f"{kind}-{idx:08d}"
    state = {
        "sourceIdentifier": {
            "ontologyType": "Work",
            "identifierType": {"id": "sierra-system-number"},
            "value": primary_value,
        }
    }
    if kind == "predecessor":
        state["sourceIdentifier"]["predecessorIdentifier"] = {  # type: ignore[index]
            "ontologyType": "Work",
            "identifierType": {"id": "calm-altref-no"},
            "value": f"pred-{idx:08d}",
        }
    items: list[dict] = []
    item_prefix = "item-existing" if items_kind == "existing" else "item"
    for j in range(n_extra):
        items.append(
            {
                "sourceIdentifier": {
                    "ontologyType": "Item",
                    "identifierType": {"id": "sierra-system-number"},
                    "value": f"{item_prefix}-{idx:08d}-{j:03d}",
                }
            }
        )
    return {"state": state, "data": {"title": f"Work {idx}"}, "items": items}


WORKLOADS: dict[str, dict] = {
    # n_works, n_extra, items_kind, split: list of (kind, fraction)
    "all-new": {
        "n_works": 1000,
        "n_extra": 5,
        "items_kind": "new",
        "split": [("new", 1.0)],
    },
    "all-existing": {
        "n_works": 1000,
        "n_extra": 5,
        "items_kind": "existing",
        "split": [("existing", 1.0)],
    },
    "mixed": {
        "n_works": 1000,
        "n_extra": 5,
        "items_kind": "new",
        "split": [("new", 0.5), ("existing", 0.4), ("predecessor", 0.1)],
    },
    "large-fanout": {
        "n_works": 200,
        "n_extra": 50,
        "items_kind": "new",
        "split": [("new", 1.0)],
    },
}


def _build_workload(workload: str) -> tuple[list[dict], list[tuple], list[tuple]]:
    """Return (docs, identifiers_to_seed, predecessor_identifiers_to_seed).

    ``identifiers_to_seed`` is a list of (source_id_tuple, canonical_id) for
    the ``existing`` kind. ``predecessor_identifiers_to_seed`` covers the
    ``predecessor`` kind's predecessor records (must exist before mint).
    """
    spec = WORKLOADS[workload]
    n_works: int = spec["n_works"]
    n_extra: int = spec["n_extra"]
    items_kind: str = spec.get("items_kind", "new")
    split: list[tuple[str, float]] = spec["split"]

    docs: list[dict] = []
    identifiers_to_seed: list[tuple[tuple[str, str, str], str]] = []
    predecessor_seed: list[tuple[tuple[str, str, str], str]] = []

    # Deterministic kind assignment by index.
    boundaries = []
    cursor = 0.0
    for kind, fraction in split:
        cursor += fraction
        boundaries.append((kind, int(round(cursor * n_works))))

    for idx in range(n_works):
        kind = "new"
        for k, upto in boundaries:
            if idx < upto:
                kind = k
                break
        docs.append(_make_work(idx, n_extra, kind, items_kind))
        if kind == "existing":
            cid = f"e{idx:07d}"[:8]
            identifiers_to_seed.append(
                (("Work", "sierra-system-number", f"existing-{idx:08d}"), cid)
            )
        elif kind == "predecessor":
            cid = f"p{idx:07d}"[:8]
            predecessor_seed.append(
                (("Work", "calm-altref-no", f"pred-{idx:08d}"), cid)
            )
        if items_kind == "existing":
            for j in range(n_extra):
                cid = f"i{idx:04d}{j:02d}"[:8]
                identifiers_to_seed.append(
                    (
                        (
                            "Item",
                            "sierra-system-number",
                            f"item-existing-{idx:08d}-{j:03d}",
                        ),
                        cid,
                    )
                )
    return docs, identifiers_to_seed, predecessor_seed


def _connect(pymysql_mod) -> object:
    return pymysql_mod.connect(
        host="localhost",
        port=3306,
        user="id_minter",
        password="id_minter",
        database="identifiers",
        cursorclass=pymysql_mod.cursors.DictCursor,
        autocommit=False,
    )


def _reset_schema(conn) -> None:
    cursor = conn.cursor()
    cursor.execute("DELETE FROM identifiers")
    cursor.execute("DELETE FROM canonical_ids")
    conn.commit()


def _seed_pool(conn, n: int) -> None:
    """Seed n free canonical IDs into the pool. Uses bulk INSERT for speed."""
    cursor = conn.cursor()
    batch = 1000
    for start in range(0, n, batch):
        rows = [(f"f{(start + i):07d}"[:8],) for i in range(min(batch, n - start))]
        placeholders = ", ".join(["(%s, 'free')"] * len(rows))
        params = [r[0] for r in rows]
        cursor.execute(
            f"INSERT INTO canonical_ids (CanonicalId, Status) VALUES {placeholders}",
            params,
        )
    conn.commit()


def _seed_identifiers(conn, mappings: list[tuple[tuple[str, str, str], str]]) -> None:
    if not mappings:
        return
    cursor = conn.cursor()
    batch = 500
    for start in range(0, len(mappings), batch):
        chunk = mappings[start : start + batch]
        # Ensure canonical_ids rows exist as 'assigned'
        cid_placeholders = ", ".join(["(%s, 'assigned')"] * len(chunk))
        cid_params = [cid for _, cid in chunk]
        cursor.execute(
            f"INSERT IGNORE INTO canonical_ids (CanonicalId, Status) "
            f"VALUES {cid_placeholders}",
            cid_params,
        )
        id_placeholders = ", ".join(["(%s, %s, %s, %s)"] * len(chunk))
        id_params: list = []
        for sid, cid in chunk:
            id_params.extend([sid[0], sid[1], sid[2], cid])
        cursor.execute(
            f"INSERT INTO identifiers (OntologyType, SourceSystem, SourceId, "
            f"CanonicalId) VALUES {id_placeholders}",
            id_params,
        )
    conn.commit()


class _CountingCursorWrapper:
    """Wrap a pymysql cursor; count execute calls by SQL prefix."""

    def __init__(self, real_cursor, counters: dict[str, int]):
        self._real = real_cursor
        self._counters = counters

    def execute(self, query, args=None):
        self._counters["total"] += 1
        stripped = query.strip().split(None, 2)
        verb = stripped[0].upper() if stripped else ""
        if verb == "INSERT":
            self._counters["inserts"] += 1
        return self._real.execute(query, args)

    def __getattr__(self, name):
        return getattr(self._real, name)


def _count_sql(resolver, counters: dict[str, int]) -> None:
    """Patch the resolver's connection so cursor() returns a counting wrapper."""
    real_cursor = resolver.conn.cursor

    def wrapped_cursor():
        return _CountingCursorWrapper(real_cursor(), counters)

    resolver.conn.cursor = wrapped_cursor  # type: ignore[method-assign]


class _NoopResolver:
    """IdResolver that fabricates canonical IDs without touching the database.

    Used as a baseline to isolate the non-DB cost of the transformer pipeline
    (embedder traversal, dict construction, etc.). If two refs report similar
    no-op timings, any wall-time delta with a real resolver is attributable to
    DB interaction — not to changes in the transformer plumbing.
    """

    def __init__(self) -> None:
        self._counter = 0

    def lookup_ids(self, source_ids):  # type: ignore[no-untyped-def]
        return {sid: self._fabricate(sid) for sid in source_ids}

    def mint_ids(self, requests):  # type: ignore[no-untyped-def]
        return {req[0]: self._fabricate(req[0]) for req in requests}

    def _fabricate(self, sid) -> str:  # type: ignore[no-untyped-def]
        self._counter += 1
        return f"n{self._counter:07d}"[:8]


def inner_main(args: argparse.Namespace) -> int:
    (
        pymysql_mod,
        IdMinterConfig,
        RDSClientConfig,
        apply_migrations,
        IdMintingTransformer,
        MintingResolver,
    ) = _inner_imports()

    config = IdMinterConfig(
        rds_client=RDSClientConfig(
            primary_host="localhost",
            replica_host="localhost",
            port=3306,
            username="id_minter",
            password="id_minter",
        ),
    )

    docs, existing_to_seed, predecessor_to_seed = _build_workload(args.workload)
    # Pool size must cover everything that might be minted (worst case = all
    # docs × (1 primary + n_extra)).
    spec = WORKLOADS[args.workload]
    pool_size = spec["n_works"] * (1 + spec["n_extra"]) + 1000

    use_noop = args.resolver == "noop"
    if not use_noop:
        apply_migrations(config)

    for iteration in range(args.iterations):
        if not use_noop:
            # Fresh schema state per iteration so timings are comparable.
            admin_conn = _connect(pymysql_mod)
            try:
                _reset_schema(admin_conn)
                _seed_pool(admin_conn, pool_size)
                _seed_identifiers(admin_conn, existing_to_seed)
                _seed_identifiers(admin_conn, predecessor_to_seed)
            finally:
                admin_conn.close()

        counters = {"total": 0, "inserts": 0}
        if use_noop:
            resolver: object = _NoopResolver()
        else:
            resolver = MintingResolver(config)
            _count_sql(resolver, counters)

        # Skip the IdMintingSource constructor entirely — it expects ES.
        transformer = object.__new__(IdMintingTransformer)
        transformer.successful_ids = []
        transformer.errors = []
        transformer.error_ids = set()
        transformer.source_id_to_row_id = {}
        transformer.resolver = resolver
        transformer.mint_batch_size = args.works_per_batch

        start = time.perf_counter()
        results = list(transformer.transform(docs))
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        # Sanity: every doc should have produced output.
        assert len(results) == len(docs), (
            f"Expected {len(docs)} outputs, got {len(results)} "
            f"(errors={transformer.errors[:3]})"
        )
        if not use_noop:
            resolver.conn.close()  # type: ignore[attr-defined]

        record = {
            "ref": args.ref,
            "workload": args.workload,
            "resolver": args.resolver,
            "iter": iteration,
            "wall_ms": elapsed_ms,
            "sql_inserts": counters["inserts"],
            "sql_total": counters["total"],
        }
        print(json.dumps(record), flush=True)
        print(
            f"[inner] {args.ref} {args.workload} resolver={args.resolver} "
            f"iter={iteration} wall={elapsed_ms:.1f}ms "
            f"inserts={counters['inserts']} total_sql={counters['total']}",
            file=sys.stderr,
        )

    return 0


# ---------------------------------------------------------------------------
# CLI plumbing
# ---------------------------------------------------------------------------


def _parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--inner-mode",
        action="store_true",
        help="Internal: invoked by the outer process inside a worktree's venv.",
    )
    parser.add_argument(
        "--refs",
        nargs="+",
        default=["main", "HEAD"],
        help="Git refs to benchmark, in order. First ref is the baseline.",
    )
    parser.add_argument(
        "--workloads",
        nargs="+",
        default=list(WORKLOADS.keys()),
        choices=list(WORKLOADS.keys()),
    )
    parser.add_argument("--iterations", type=int, default=5)
    parser.add_argument("--works-per-batch", type=int, default=500)
    parser.add_argument("--keep-worktrees", action="store_true")
    parser.add_argument(
        "--include-noop",
        action="store_true",
        help="Also run each (ref, workload) with a no-op resolver to expose "
        "non-DB cost as a baseline.",
    )
    parser.add_argument("--output", choices=["markdown", "json"], default="markdown")
    # Inner-mode-only fields:
    parser.add_argument("--ref", help="(inner) ref label for output records")
    parser.add_argument("--workload", help="(inner) single workload to run")
    parser.add_argument(
        "--resolver",
        choices=["mysql", "noop"],
        default="mysql",
        help="(inner) resolver implementation to time",
    )
    return parser.parse_args(list(argv))


def main(argv: Iterable[str]) -> int:
    args = _parse_args(argv)
    if args.inner_mode:
        if not args.ref or not args.workload:
            print("--inner-mode requires --ref and --workload", file=sys.stderr)
            return 2
        return inner_main(args)
    return outer_main(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
