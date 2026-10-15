"""Time a probe step and record it as a CloudWatch Embedded Metric Format line."""

import json
import time
from collections.abc import Callable, Iterator
from contextlib import AbstractContextManager, contextmanager

import httpx

NAMESPACE = "third_party_probes"

Timer = Callable[[str], AbstractContextManager[None]]


def _outcome(error: Exception) -> str:
    if isinstance(error, (httpx.TimeoutException, TimeoutError)):
        return "timeout"
    if isinstance(error, httpx.HTTPStatusError):
        return "http_error"
    return "error"


def emit(
    vendor: str,
    step: str,
    outcome: str,
    duration_ms: float,
    status_code: int | None = None,
    error: str | None = None,
) -> None:
    record = {
        "_aws": {
            "Timestamp": int(time.time() * 1000),
            "CloudWatchMetrics": [
                {
                    "Namespace": NAMESPACE,
                    "Dimensions": [["Vendor", "Step", "Outcome"]],
                    "Metrics": [{"Name": "Duration", "Unit": "Milliseconds"}],
                }
            ],
        },
        "Vendor": vendor,
        "Step": step,
        "Outcome": outcome,
        "Duration": round(duration_ms, 1),
        "status_code": status_code,
        "error": error,
    }
    # Printed rather than logged: CloudWatch only extracts EMF from a bare JSON line.
    print(json.dumps(record), flush=True)


def timer_for(vendor: str) -> Timer:
    @contextmanager
    def timed(step: str) -> Iterator[None]:
        start = time.monotonic()
        try:
            yield
        except Exception as e:
            status = (
                e.response.status_code if isinstance(e, httpx.HTTPStatusError) else None
            )
            duration_ms = (time.monotonic() - start) * 1000
            emit(vendor, step, _outcome(e), duration_ms, status, repr(e)[:500])
            raise
        emit(vendor, step, "ok", (time.monotonic() - start) * 1000)

    return timed
