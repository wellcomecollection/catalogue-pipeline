from models.events import BasePipelineEvent, PipelineIndexDates
from utils.elasticsearch import get_merged_index_name


def test_get_merged_index_name_uses_merged_date() -> None:
    event = BasePipelineEvent(
        pipeline_date="2023-01-01",
        index_dates=PipelineIndexDates(merged="2023-02-02"),
    )
    assert get_merged_index_name(event) == "works-denormalised-2023-02-02"


def test_get_merged_index_name_uses_pipeline_date_if_merged_date_missing() -> None:
    event = BasePipelineEvent(
        pipeline_date="2023-01-01", index_dates=PipelineIndexDates(merged=None)
    )
    assert get_merged_index_name(event) == "works-denormalised-2023-01-01"
