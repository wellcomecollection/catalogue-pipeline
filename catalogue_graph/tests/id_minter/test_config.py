"""Tests for IdMinterConfig index name properties."""

from __future__ import annotations

from id_minter.config import IdMinterConfig


class TestSourceIndexName:
    def test_uses_pipeline_date_by_default(self) -> None:
        cfg = IdMinterConfig(pipeline_date="2025-01-01")
        assert cfg.source_index_name == "works-source-2025-01-01"

    def test_uses_source_index_date_suffix_when_set(self) -> None:
        cfg = IdMinterConfig(
            pipeline_date="2025-01-01",
            source_index_date_suffix="2024-12-01",
        )
        assert cfg.source_index_name == "works-source-2024-12-01"

    def test_ignores_target_index_date_suffix(self) -> None:
        cfg = IdMinterConfig(
            pipeline_date="2025-01-01",
            target_index_date_suffix="2024-12-01",
        )
        assert cfg.source_index_name == "works-source-2025-01-01"

    def test_respects_custom_source_index_prefix(self) -> None:
        cfg = IdMinterConfig(
            source_index_prefix="custom-source",
            pipeline_date="2025-01-01",
            source_index_date_suffix="2024-06-01",
        )
        assert cfg.source_index_name == "custom-source-2024-06-01"


class TestTargetIndexName:
    def test_uses_pipeline_date_by_default(self) -> None:
        cfg = IdMinterConfig(pipeline_date="2025-01-01")
        assert cfg.target_index_name == "works-identified-2025-01-01"

    def test_uses_target_index_date_suffix_when_set(self) -> None:
        cfg = IdMinterConfig(
            pipeline_date="2025-01-01",
            target_index_date_suffix="2024-12-01",
        )
        assert cfg.target_index_name == "works-identified-2024-12-01"

    def test_ignores_source_index_date_suffix(self) -> None:
        cfg = IdMinterConfig(
            pipeline_date="2025-01-01",
            source_index_date_suffix="2024-12-01",
        )
        assert cfg.target_index_name == "works-identified-2025-01-01"

    def test_respects_custom_target_index_prefix(self) -> None:
        cfg = IdMinterConfig(
            target_index_prefix="custom-target",
            pipeline_date="2025-01-01",
            target_index_date_suffix="2024-06-01",
        )
        assert cfg.target_index_name == "custom-target-2024-06-01"


class TestIndexNameIndependence:
    def test_source_and_target_can_have_different_dates(self) -> None:
        cfg = IdMinterConfig(
            pipeline_date="2025-01-01",
            source_index_date_suffix="2024-11-01",
            target_index_date_suffix="2025-02-01",
        )
        assert cfg.source_index_name == "works-source-2024-11-01"
        assert cfg.target_index_name == "works-identified-2025-02-01"

    def test_no_overrides_both_use_pipeline_date(self) -> None:
        cfg = IdMinterConfig(pipeline_date="2025-03-06")
        assert cfg.source_index_name == "works-source-2025-03-06"
        assert cfg.target_index_name == "works-identified-2025-03-06"
