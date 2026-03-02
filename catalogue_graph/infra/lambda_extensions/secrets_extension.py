#!/usr/bin/env python3
"""Lambda extension that resolves secrets from environment variables.

Replaces the bash_secrets_extension.sh with a Python implementation that:
- Uses boto3 (pre-installed in the Lambda Python base image) instead of AWS CLI
- Supports JSON secret parsing via the syntax: secret:<name>:<json_key>
- Writes resolved secrets to /tmp/.env for consumption by python-dotenv

Environment variables with values matching these patterns are resolved:
  secret:<secret_name>              — plain string secret
  secret:<secret_name>:<json_key>   — extract a key from a JSON secret

The resolved values are written to /tmp/.env as KEY="value" pairs.
"""

from __future__ import annotations

import json
import os
import signal
import sys
from typing import Any, cast
from urllib.request import Request, urlopen

import boto3

EXTENSION_NAME = os.path.basename(__file__)
DOTENV_FILE = "/tmp/.env"
RUNTIME_API = os.environ.get("AWS_LAMBDA_RUNTIME_API", "")
SECRET_PREFIX = "secret:"


def log(msg: str) -> None:
    print(f"[{EXTENSION_NAME}] {msg}", flush=True)


def resolve_secret(secret_ref: str) -> tuple[str, str | None]:
    """Parse a secret reference and return (secret_name, json_key_or_None)."""
    # secret:my-secret-name or secret:my-secret-name:json_key
    without_prefix = secret_ref[len(SECRET_PREFIX) :]
    parts = without_prefix.split(":", 1)
    secret_name = parts[0]
    json_key = parts[1] if len(parts) > 1 else None
    return secret_name, json_key


def fetch_secret(client: Any, secret_name: str, json_key: str | None) -> str:
    """Fetch a secret value from Secrets Manager."""
    response = client.get_secret_value(SecretId=secret_name)
    secret_string: str = response["SecretString"]

    if json_key is not None:
        parsed = json.loads(secret_string)
        value = parsed[json_key]
        return str(value)

    return secret_string


def create_dotenv() -> None:
    """Scan env vars for secret: prefixes, resolve them, write /tmp/.env."""
    client = boto3.client("secretsmanager")

    with open(DOTENV_FILE, "w") as f:
        for key, value in os.environ.items():
            if not value.startswith(SECRET_PREFIX):
                continue

            secret_name, json_key = resolve_secret(value)
            key_info = f", key: {json_key}" if json_key else ""
            log(f"Resolving secret for {key} (secret: {secret_name}{key_info})")

            try:
                resolved = fetch_secret(client, secret_name, json_key)
            except Exception as exc:
                log(f"Failed to resolve secret {secret_name}: {exc}")
                sys.exit(1)

            # Escape backslashes and double quotes for .env safety
            escaped = resolved.replace("\\", "\\\\").replace('"', '\\"')
            f.write(f'{key}="{escaped}"\n')

    log(f"Wrote secrets to {DOTENV_FILE}")


def register() -> str:
    """Register this extension with the Lambda Extensions API."""
    url = f"http://{RUNTIME_API}/2020-01-01/extension/register"
    data = json.dumps({"events": ["SHUTDOWN"]}).encode()
    req = Request(url, data=data, method="POST")
    req.add_header("Lambda-Extension-Name", EXTENSION_NAME)
    req.add_header("Content-Type", "application/json")

    with urlopen(req) as resp:
        extension_id = str(resp.headers["Lambda-Extension-Identifier"])
        log(f"Registered with extension ID: {extension_id}")
        return extension_id


def next_event(extension_id: str) -> dict[str, Any]:
    """Block until the next event from the Extensions API."""
    url = f"http://{RUNTIME_API}/2020-01-01/extension/event/next"
    req = Request(url, method="GET")
    req.add_header("Lambda-Extension-Identifier", extension_id)

    with urlopen(req) as resp:
        return cast(dict[str, Any], json.loads(resp.read()))


def main() -> None:
    log("Initialization")

    create_dotenv()

    extension_id = register()

    def handle_sigterm(signum: int, frame: Any) -> None:
        log("Received SIGTERM, exiting")
        sys.exit(0)

    signal.signal(signal.SIGTERM, handle_sigterm)

    while True:
        log("Waiting for next event...")
        event = next_event(extension_id)
        if event.get("eventType") == "SHUTDOWN":
            log("Received SHUTDOWN event, exiting")
            break


if __name__ == "__main__":
    main()
