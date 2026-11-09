from unittest.mock import MagicMock

import pytest

from graph.steps import pit_opener
from models.events import BasePipelineEvent

WINDOW = {"start_time": "2026-01-01T10:00:00Z", "end_time": "2026-01-01T10:15:00Z"}


@pytest.fixture
def es_client(monkeypatch: pytest.MonkeyPatch) -> MagicMock:
    client = MagicMock()
    client.open_point_in_time.side_effect = lambda index, keep_alive: {
        "id": f"pit-{index}"
    }
    monkeypatch.setattr(pit_opener, "get_client", lambda *_args: client)
    return client


def _event(**kwargs: object) -> BasePipelineEvent:
    return BasePipelineEvent.model_validate(
        {"pipeline_date": "dev", "graph_date": "dev", **kwargs}
    )


def test_skips_pits_when_nothing_changed_in_window(es_client: MagicMock) -> None:
    es_client.count.return_value = {"count": 0}

    result = pit_opener.handler(_event(window=WINDOW))

    assert result == {"has_changes": False}
    assert es_client.count.call_count == 2
    es_client.open_point_in_time.assert_not_called()


@pytest.mark.parametrize(("counts", "count_calls"), [([1], 1), ([0, 1], 2)])
def test_opens_pits_when_works_or_images_changed(
    es_client: MagicMock, counts: list[int], count_calls: int
) -> None:
    es_client.count.side_effect = [{"count": c} for c in counts]

    result = pit_opener.handler(_event(window=WINDOW))

    assert result["has_changes"] is True
    assert set(result["pit_ids"]) == {"merged", "augmented"}
    assert es_client.open_point_in_time.call_count == 2
    # Images aren't counted once works have changed
    assert es_client.count.call_count == count_calls


def test_counts_use_the_window_on_each_index_time_field(
    es_client: MagicMock,
) -> None:
    es_client.count.return_value = {"count": 0}

    pit_opener.handler(_event(window=WINDOW))

    fields = [
        next(iter(call.kwargs["query"]["range"]))
        for call in es_client.count.call_args_list
    ]
    assert fields == ["state.mergedTime", "state.augmentedTime"]


def test_opens_pits_without_counting_outside_window_mode(
    es_client: MagicMock,
) -> None:
    result = pit_opener.handler(_event(ids=["abc12345"]))

    assert result["has_changes"] is True
    es_client.count.assert_not_called()
    assert es_client.open_point_in_time.call_count == 2
