#!/usr/bin/env python3
"""Print one line per cell from harness output files (one result, or a list from invoke)."""

import json
import sys


def cells(result: dict) -> list[dict]:
    return result.get("cells", [result]) if "cells" in result or "docs_per_second" in result else []


def main() -> None:
    for path in sys.argv[1:]:
        data = json.load(open(path))
        results = data if isinstance(data, list) else [data]
        for r in results:
            label = f"{r.get('target', '?')} {r.get('mode', '?')}"
            if "invocation" in r:
                label += f" #{r['invocation']}"
            if "function_error" in r:
                print(f"{label}: ERROR {r.get('errorMessage', r)}")
                continue
            if r.get("mode") == "ping":
                lat = r["latency"]
                print(f"{label}: p50 {lat['p50_ms']:.1f}ms p95 {lat['p95_ms']:.1f}ms p99 {lat['p99_ms']:.1f}ms")
                continue
            for c in cells(r):
                lat = c["latency"]
                print(
                    f"{label}: b{c.get('bulk_size', '-'):>3} c{c.get('concurrency', '-'):>2}"
                    f"  docs {c.get('docs', c.get('copied', 0)):>6}  docs/s {c['docs_per_second']:8.1f}"
                    f"  p50 {lat['p50_ms']:7.1f}ms  p95 {lat['p95_ms']:7.1f}ms  p99 {lat['p99_ms']:7.1f}ms"
                    f"  429s {c.get('http_429s', 0)}/{c.get('item_429s', 0)}  retries {c.get('retries', 0)}"
                    f"  errors {c.get('item_errors', 0)}  failed {c.get('failed_requests', 0)}"
                )


if __name__ == "__main__":
    main()
