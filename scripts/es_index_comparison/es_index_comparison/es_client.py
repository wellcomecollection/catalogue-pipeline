from __future__ import annotations

from elasticsearch import Elasticsearch
from typing import Optional


def build_client(cloud_id: Optional[str], api_key: Optional[str]) -> Elasticsearch:
    if not cloud_id:
        raise ValueError("cloud_id not provided (flag or ES_CLOUD_ID env)")
    if not api_key:
        raise ValueError("api_key not provided (flag or ES_API_KEY env)")
    # Read-only usage: we rely on provided API key permissions; no writes performed by code.
    return Elasticsearch(cloud_id=cloud_id, api_key=api_key)
