"""
FOLIO OKAPI credential resolution.

Kept separate from the sync loop so ``run_sync`` stays free of environment / SSM
concerns: the handler resolves config here, then injects the built FOLIO client.
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from typing import Any, cast

import boto3

# ── lazy singletons (survive across warm Lambda invocations) ──────────────────


@lru_cache(maxsize=1)
def _ssm() -> Any:
    return boto3.client("ssm", region_name=os.environ["AWS_REGION"])


# ── OKAPI config ──────────────────────────────────────────────────────────────


def load_okapi_config() -> dict[str, str]:
    """FOLIO OKAPI url/tenant/username/password from env and/or SSM.

    Per-field env overrides (OKAPI_URL / OKAPI_TENANT / OKAPI_USERNAME /
    OKAPI_PASSWORD) let local runs skip SSM entirely; in Lambda these usually
    come from the OKAPI_SECRET_PARAM SecureString JSON.
    """
    data: dict[str, str] = {}
    param_name = os.environ.get("OKAPI_SECRET_PARAM")
    if param_name:
        param = _ssm().get_parameter(Name=param_name, WithDecryption=True)
        data = json.loads(param["Parameter"]["Value"])

    merged = {
        "url": os.environ.get("OKAPI_URL") or data.get("url"),
        "tenant": os.environ.get("OKAPI_TENANT") or data.get("tenant"),
        "username": os.environ.get("OKAPI_USERNAME") or data.get("username"),
        "password": os.environ.get("OKAPI_PASSWORD") or data.get("password"),
    }
    missing = [key for key, value in merged.items() if not value]
    if missing:
        missing_list = ", ".join(missing)
        raise ValueError(
            "Missing OKAPI configuration fields: "
            f"{missing_list}. Provide OKAPI_* env vars or set OKAPI_SECRET_PARAM."
        )

    return cast("dict[str, str]", merged)
