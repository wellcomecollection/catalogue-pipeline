"""Shared input handling for find_work steps."""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

# Window end lags the scheduled time so recently written documents have indexed.
SCHEDULE_INDEXING_LAG = timedelta(minutes=5)


def normalise_lambda_input(event: dict, defaults: dict[str, Any]) -> dict:
    """Normalise a find-work Lambda payload into FindWorkEvent fields.

    Scheduled runs send scheduled_time, which becomes the window end minus an
    indexing lag; replays pass ids (source_identifiers is accepted as an alias)
    or an explicit window. With no scope at all, full: true is required so a
    malformed invoke fails loudly instead of scanning the whole index.
    Deployment-identity defaults (pipeline_date etc.) fill only absent fields.
    """
    data = {k: v for k, v in event.items() if v is not None}
    scheduled_time = data.pop("scheduled_time", None)
    full = data.pop("full", None)

    if data.get("ids") is None:
        alias = data.pop("source_identifiers", None)
        if alias is not None:
            data["ids"] = alias

    if data.get("ids") is None and data.get("window") is None:
        if scheduled_time is not None:
            end_time = datetime.fromisoformat(scheduled_time) - SCHEDULE_INDEXING_LAG
            data["window"] = {"end_time": end_time}
        elif full is not True:
            raise ValueError(
                "No ids, window or scheduled_time given; "
                "pass 'full': true to scan the whole index."
            )

    for key, value in defaults.items():
        data.setdefault(key, value)
    return data
