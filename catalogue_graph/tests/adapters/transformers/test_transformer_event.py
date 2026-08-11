"""Tests for TransformerEvent's ids/changeset_ids validation."""

import pytest

from adapters.steps.transformer import MAX_IDS_PER_RUN, TransformerEvent


def _event(**overrides: object) -> TransformerEvent:
    payload: dict[str, object] = {
        "transformer_type": "ebsco",
        "job_id": "job-1",
    }
    payload.update(overrides)
    return TransformerEvent.model_validate(payload)


def test_no_ids_or_changesets_is_a_valid_reindex_event() -> None:
    event = _event()
    assert event.ids is None
    assert event.changeset_ids == []


def test_changeset_ids_alone_is_valid() -> None:
    event = _event(changeset_ids=["cs1"])
    assert event.ids is None
    assert event.changeset_ids == ["cs1"]


def test_ids_alone_is_valid() -> None:
    event = _event(ids=["rec1", "rec2"])
    assert event.ids == ["rec1", "rec2"]
    assert event.changeset_ids == []


def test_rejects_ids_combined_with_changeset_ids() -> None:
    with pytest.raises(ValueError, match="mutually exclusive"):
        _event(ids=["rec1"], changeset_ids=["cs1"])


def test_rejects_an_explicitly_empty_id_list() -> None:
    """An id query that came back empty must fail, not silently fall through
    to a full reindex."""
    with pytest.raises(ValueError, match="at least one record id"):
        _event(ids=[])


def test_rejects_a_run_over_the_id_ceiling() -> None:
    with pytest.raises(ValueError, match="exceeds"):
        _event(ids=[f"rec{i}" for i in range(MAX_IDS_PER_RUN + 1)])


def test_allows_a_run_at_the_id_ceiling() -> None:
    event = _event(ids=[f"rec{i}" for i in range(MAX_IDS_PER_RUN)])
    assert len(event.ids or []) == MAX_IDS_PER_RUN
