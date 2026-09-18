"""Writing and reading a works-indexed document at an explicit external version."""

import copy
from typing import Any

from elasticsearch import ApiError, Elasticsearch


def build_work(template: dict, marker: int, source: str, merged: str) -> dict:
    """A works-indexed document stamped so a test can tell which write won.

    `debug` is mapped `dynamic: false`, so these round-trip through `_source` without
    needing a mapping change.
    """
    work = copy.deepcopy(template)
    work["debug"]["source"]["version"] = marker
    work["debug"]["source"]["modifiedTime"] = source
    work["debug"]["mergedTime"] = merged
    return work


def write_work(
    es_client: Elasticsearch,
    index: str,
    doc_id: str,
    version: Any,
    document: dict,
    version_type: str = "external_gte",
) -> tuple[bool, int, str]:
    """Index one work at an explicit external version.

    Returns (accepted, status, reason) rather than raising, so a test can assert on a
    rejection as readily as on a success.
    """
    try:
        es_client.index(
            index=index,
            id=doc_id,
            document=document,
            version=version,
            version_type=version_type,
        )
    except ApiError as error:
        body = error.body if isinstance(error.body, dict) else {}
        reason = (body.get("error") or {}).get("reason") or str(error)
        return False, error.status_code or 0, reason
    return True, 200, ""


def stored_work(es_client: Elasticsearch, index: str, doc_id: str) -> tuple[int, int]:
    """(marker, _version) of whichever write is stored. A get by id is realtime."""
    document = es_client.get(index=index, id=doc_id)
    return document["_source"]["debug"]["source"]["version"], document["_version"]
