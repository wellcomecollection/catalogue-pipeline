from __future__ import annotations

import pytest
import yaml

from es_index_comparison.source_config import load_source_configuration


def _write_source_config(tmp_path, clusters):
    path = tmp_path / "source_configuration.yaml"
    raw = {
        "clusters": clusters,
        "index_sources": {"side-a": {"cluster": "a", "index": "works-a"}},
    }
    path.write_text(yaml.safe_dump(raw))
    return path


def test_cluster_can_be_given_by_host(tmp_path):
    path = _write_source_config(
        tmp_path, {"a": {"host": "https://example.test:443", "api_key": "key"}}
    )

    source = load_source_configuration(path).resolve_index_sources(["side-a"])[0]

    assert source.host == "https://example.test:443"
    assert source.cloud_id is None


def test_cluster_can_be_given_by_cloud_id(tmp_path):
    path = _write_source_config(tmp_path, {"a": {"cloud_id": "cloud", "api_key": "key"}})

    source = load_source_configuration(path).resolve_index_sources(["side-a"])[0]

    assert source.cloud_id == "cloud"
    assert source.host is None


def test_cluster_needs_cloud_id_or_host(tmp_path):
    path = _write_source_config(tmp_path, {"a": {"api_key": "key"}})

    with pytest.raises(ValueError, match="cloud_id or host"):
        load_source_configuration(path)
