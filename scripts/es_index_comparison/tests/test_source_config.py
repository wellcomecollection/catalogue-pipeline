from __future__ import annotations

import pytest
import yaml

from es_index_comparison.source_config import Cluster, load_source_configuration


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

    assert source.cluster.host == "https://example.test:443"
    assert source.cluster.cloud_id is None


def test_cluster_can_be_given_by_cloud_id(tmp_path):
    path = _write_source_config(tmp_path, {"a": {"cloud_id": "cloud", "api_key": "key"}})

    source = load_source_configuration(path).resolve_index_sources(["side-a"])[0]

    assert source.cluster.cloud_id == "cloud"
    assert source.cluster.host is None


def test_cluster_needs_cloud_id_or_host(tmp_path):
    path = _write_source_config(tmp_path, {"a": {"api_key": "key"}})

    with pytest.raises(ValueError, match="cloud_id or host"):
        load_source_configuration(path)


def test_validation_errors_do_not_print_api_keys(tmp_path):
    path = _write_source_config(tmp_path, {"a": {"api_key": "very-secret"}})

    with pytest.raises(ValueError) as error:
        load_source_configuration(path)

    assert "very-secret" not in str(error.value)


def test_validation_errors_of_a_cluster_alone_do_not_print_api_keys():
    with pytest.raises(ValueError) as error:
        Cluster.model_validate({"api_key": "very-secret"})

    assert "very-secret" not in str(error.value)


def test_printing_an_index_source_does_not_show_its_api_key(tmp_path):
    path = _write_source_config(tmp_path, {"a": {"host": "h", "api_key": "very-secret"}})

    source = load_source_configuration(path).resolve_index_sources(["side-a"])[0]

    assert "very-secret" not in repr(source)
