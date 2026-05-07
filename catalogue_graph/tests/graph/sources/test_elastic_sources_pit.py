from unittest.mock import MagicMock

from graph.sources.augmented_images_source import AugmentedImagesSource
from graph.sources.merged_works_source import MergedWorksSource
from models.events import BasePipelineEvent, PipelinePitIds


def test_merged_works_source_uses_merged_pit_id() -> None:
    event = BasePipelineEvent(
        pipeline_date="2025-01-01",
        pit_ids=PipelinePitIds(merged="merged_pit_123", augmented="augmented_pit_456"),
    )
    es_client = MagicMock()

    source = MergedWorksSource(event=event, es_client=es_client)

    assert source.pit_id == "merged_pit_123"
    es_client.open_point_in_time.assert_not_called()


def test_augmented_images_source_uses_augmented_pit_id() -> None:
    event = BasePipelineEvent(
        pipeline_date="2025-01-01",
        pit_ids=PipelinePitIds(merged="merged_pit_123", augmented="augmented_pit_456"),
    )
    es_client = MagicMock()

    source = AugmentedImagesSource(event=event, es_client=es_client)

    assert source.pit_id == "augmented_pit_456"
    es_client.open_point_in_time.assert_not_called()


def test_merged_works_source_opens_pit_when_none() -> None:
    event = BasePipelineEvent(pipeline_date="2025-01-01")
    es_client = MagicMock()
    es_client.open_point_in_time.return_value = {"id": "fresh_merged_pit"}

    source = MergedWorksSource(event=event, es_client=es_client)

    assert source.pit_id == "fresh_merged_pit"
    es_client.open_point_in_time.assert_called_once()


def test_augmented_images_source_opens_pit_when_none() -> None:
    event = BasePipelineEvent(pipeline_date="2025-01-01")
    es_client = MagicMock()
    es_client.open_point_in_time.return_value = {"id": "fresh_augmented_pit"}

    source = AugmentedImagesSource(event=event, es_client=es_client)

    assert source.pit_id == "fresh_augmented_pit"
    es_client.open_point_in_time.assert_called_once()
