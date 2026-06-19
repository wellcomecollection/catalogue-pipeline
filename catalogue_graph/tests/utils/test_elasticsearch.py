import elasticsearch
import pytest
from _pytest.monkeypatch import MonkeyPatch

from models.events import BasePipelineEvent, PipelineIndexDates
from utils import elasticsearch as es_utils
from utils.elasticsearch import get_merged_index_name, index_es_batch


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


def test_index_es_batch_retries_transient_connection_error(
    monkeypatch: MonkeyPatch,
) -> None:
    monkeypatch.setattr(es_utils, "ES_BULK_BACKOFF_SECONDS", 0.0)
    calls = {"n": 0}

    def fake_bulk(client: object, actions: list, **kwargs: object) -> tuple:
        calls["n"] += 1
        if calls["n"] == 1:
            raise elasticsearch.exceptions.ConnectionError("boom")
        return len(actions), []

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    success_count, errors = index_es_batch(None, [{"_id": "1"}])  # type: ignore[arg-type]

    assert calls["n"] == 2
    assert success_count == 1
    assert errors == []


def test_index_es_batch_raises_after_exhausting_retries(
    monkeypatch: MonkeyPatch,
) -> None:
    monkeypatch.setattr(es_utils, "ES_BULK_BACKOFF_SECONDS", 0.0)
    monkeypatch.setattr(es_utils, "ES_BULK_MAX_ATTEMPTS", 3)
    calls = {"n": 0}

    def fake_bulk(client: object, actions: list, **kwargs: object) -> tuple:
        calls["n"] += 1
        raise elasticsearch.exceptions.ConnectionError("down")

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    with pytest.raises(elasticsearch.exceptions.ConnectionError):
        index_es_batch(None, [{"_id": "1"}])  # type: ignore[arg-type]

    assert calls["n"] == 3
