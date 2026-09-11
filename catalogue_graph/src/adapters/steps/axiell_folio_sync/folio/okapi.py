"""
FOLIO OKAPI credential resolution.

Kept separate from the sync loop so ``run_sync`` stays free of environment / SSM
concerns: the handler resolves config here, then injects the built FOLIO client.

Two FOLIO instances can be targeted: ``prod`` (the EBSCO SaaS tenant, the
default) and ``dev`` (the folio-dev-server sandbox). Each has its own SSM
SecureString, named by ``OKAPI_SECRET_PARAM`` and ``OKAPI_DEV_SECRET_PARAM``
respectively, so pointing a run at the sandbox never touches the prod config.
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from typing import Any, cast

import boto3

from adapters.steps.axiell_folio_sync.models import FolioTarget

# ── lazy singletons (survive across warm Lambda invocations) ──────────────────


@lru_cache(maxsize=1)
def _ssm() -> Any:
    return boto3.client("ssm", region_name=os.environ["AWS_REGION"])


# ── FOLIO target ──────────────────────────────────────────────────────────────

# Which env var names the SSM SecureString for each target. Typed by FolioTarget
# so this registry and the event model cannot drift apart: a key that is not a
# valid target fails type checking rather than at runtime, on a dev-targeted run.
SECRET_PARAM_ENV_VARS: dict[FolioTarget, str] = {
    "prod": "OKAPI_SECRET_PARAM",
    "dev": "OKAPI_DEV_SECRET_PARAM",
}
DEFAULT_FOLIO_TARGET: FolioTarget = "prod"


def resolve_folio_target(target: str | None = None) -> FolioTarget:
    """Which FOLIO instance to talk to: the explicit argument, else FOLIO_TARGET,
    else prod.
    """
    resolved = (target or os.environ.get("FOLIO_TARGET") or "").strip().lower()
    resolved = resolved or DEFAULT_FOLIO_TARGET
    if resolved not in SECRET_PARAM_ENV_VARS:
        valid = ", ".join(sorted(SECRET_PARAM_ENV_VARS))
        raise ValueError(
            f"Unknown FOLIO target '{resolved}' (expected one of: {valid})"
        )
    return cast("FolioTarget", resolved)


# ── OKAPI config ──────────────────────────────────────────────────────────────


def load_okapi_config(target: str | None = None) -> dict[str, str]:
    """FOLIO OKAPI url/tenant/username/password from env and/or SSM.

    ``target`` selects the FOLIO instance (see :func:`resolve_folio_target`) and
    therefore which SSM SecureString is read. A dev-targeted run never falls back
    to the prod parameter: if OKAPI_DEV_SECRET_PARAM is unset and the OKAPI_* env
    vars are incomplete, it fails rather than quietly writing to production.

    Per-field env overrides (OKAPI_URL / OKAPI_TENANT / OKAPI_USERNAME /
    OKAPI_PASSWORD) let local runs skip SSM entirely; in Lambda these usually
    come from the SecureString JSON.
    """
    resolved_target = resolve_folio_target(target)
    param_env_var = SECRET_PARAM_ENV_VARS[resolved_target]

    data: dict[str, str] = {}
    param_name = os.environ.get(param_env_var)
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
            f"Missing OKAPI configuration fields for the '{resolved_target}' FOLIO "
            f"target: {missing_list}. Provide OKAPI_* env vars or set "
            f"{param_env_var}."
        )

    return cast("dict[str, str]", merged)
