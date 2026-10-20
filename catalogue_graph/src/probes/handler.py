"""Probe one third-party vendor's API and record its latency and availability.

Invoked by one EventBridge schedule per vendor with ``{"vendor": "<name>"}``.
"""

import json
import os
from collections.abc import Callable
from typing import Any

import httpx
import structlog

from probes import vendors
from probes.emf import Timer, emit, timer_for
from utils.aws import get_ssm_parameter

logger = structlog.get_logger(__name__)

PROBES: dict[str, Callable[[dict, httpx.Client, Timer], None]] = {
    "sierra": vendors.sierra,
    "axiell_oai": vendors.axiell_oai,
    "folio_oai": vendors.folio_oai,
    "folio_okapi": vendors.folio_okapi,
    "ebsco_ftp": vendors.ebsco_ftp,
}

REQUIRED_FIELDS = {
    "sierra": ("url", "key", "secret"),
    "axiell_oai": ("url", "token"),
    "folio_oai": ("url", "token"),
    "folio_okapi": ("url", "tenant", "username", "password"),
    "ebsco_ftp": ("server", "username", "password", "remote_dir"),
}

CREDENTIALS_PREFIX = os.getenv(
    "CREDENTIALS_PREFIX", "/catalogue_pipeline/third_party_probes"
)


def run(vendor: str, client: httpx.Client | None = None) -> bool:
    try:
        probe = PROBES[vendor]
        creds = json.loads(get_ssm_parameter(f"{CREDENTIALS_PREFIX}/{vendor}"))
        missing = [k for k in REQUIRED_FIELDS[vendor] if not creds.get(k)]
        if missing:
            raise ValueError(f"credentials missing {', '.join(missing)}")
    except Exception as e:
        # Recorded so a missing or unfilled parameter shows in the metrics.
        emit(vendor, "setup", "error", 0, error=repr(e)[:500])
        logger.exception("Probe setup failed", vendor=vendor)
        return False

    timeout = httpx.Timeout(
        vendors.TIMEOUT_SECONDS, connect=vendors.CONNECT_TIMEOUT_SECONDS
    )

    with client or httpx.Client(timeout=timeout) as c:
        try:
            probe(creds, c, timer_for(vendor))
        except Exception:
            # Already recorded as a metric; returning normally stops Lambda retrying.
            logger.exception("Probe failed", vendor=vendor)
            return False
    return True


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    vendor = event["vendor"]
    return {"vendor": vendor, "ok": run(vendor)}
