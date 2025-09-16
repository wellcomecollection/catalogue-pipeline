"""Send a Step Function-style request to the locally running id-minter lambda.

Simplified version: reads ONLY newline-separated source identifiers from STDIN
and posts a single JSON object to the local lambda runtime API on port 9000.

Expected payload shape:
    {
        "sourceIdentifiers": ["id1", "id2", ...],
        "jobId": "local-<uuid>"
    }

Usage:
    # Install deps (from this directory) with uv:
    #   uv sync
    # Run:
    cat source_ids.txt | uv run post_to_rie.py

If run without piped input, the script exits with an error.

Note: Duplicate source identifiers are preserved (useful for testing error /
idempotency scenarios).
"""

from __future__ import annotations

import json
import sys
import uuid
from typing import List

import requests

DEFAULT_URL = "http://localhost:9000/2015-03-31/functions/function/invocations"


def read_stdin_source_ids() -> List[str]:
    if sys.stdin.isatty():
        print("Expected newline-separated source IDs on stdin", file=sys.stderr)
        sys.exit(1)
    return [line.strip() for line in sys.stdin if line.strip()]


def build_payload(source_ids: List[str]) -> dict:
    return {"sourceIdentifiers": source_ids, "jobId": f"local-{uuid.uuid4()}"}


def main():
    source_ids = read_stdin_source_ids()
    if not source_ids:
        print(
            "No source identifiers provided on stdin. Nothing to do.", file=sys.stderr
        )
        sys.exit(1)

    payload = build_payload(source_ids)
    data = json.dumps(payload)

    resp = requests.post(
        DEFAULT_URL, data=data, headers={"Content-Type": "application/json"}
    )
    try:
        resp.raise_for_status()
    except Exception as e:  # pragma: no cover - simple debug output
        print(
            f"Request failed: {e}\nStatus: {resp.status_code}\nBody: {resp.text}",
            file=sys.stderr,
        )
        sys.exit(2)

    # Print the lambda response (already JSON)
    print(resp.text)


if __name__ == "__main__":  # pragma: no cover
    main()
