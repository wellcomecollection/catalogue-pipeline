"""Tests for StepFunctionMintingRequest camelCase / snake_case handling."""

from datetime import datetime, timedelta

import pytest

from id_minter.models.step_events import (
    DEFAULT_WINDOW_MINUTES,
    # SourceQueryRequest,
    StepFunctionMintingRequest,
)


class TestStepFunctionMintingRequestCaseHandling:
    def test_accepts_identifiers_camel_case(self) -> None:
        request = StepFunctionMintingRequest.model_validate(
            {"sourceIdentifiers": ["sierra/1", "sierra/2"], "jobId": "job-001"}
        )
        assert request.source_identifiers == ["sierra/1", "sierra/2"]
        assert request.job_id == "job-001"

    def test_accepts_identifiers_snake_case(self) -> None:
        request = StepFunctionMintingRequest.model_validate(
            {"source_identifiers": ["sierra/1"], "job_id": "job-002"}
        )
        assert request.source_identifiers == ["sierra/1"]
        assert request.job_id == "job-002"

    def test_model_dump_returns_snake_case(self) -> None:
        request = StepFunctionMintingRequest.model_validate(
            {"sourceIdentifiers": ["sierra/1"], "jobId": "job-003"}
        )
        dumped = request.model_dump()
        assert "source_identifiers" in dumped
        assert "job_id" in dumped
        assert "sourceIdentifiers" not in dumped
        assert "jobId" not in dumped

    def test_rejects_empty_source_identifier_camel_case(self) -> None:
        with pytest.raises(ValueError, match="cannot contain empty strings"):
            StepFunctionMintingRequest.model_validate(
                {"sourceIdentifiers": ["sierra/1", "  "], "jobId": "job-004"}
            )

    def test_rejects_empty_job_id_camel_case(self) -> None:
        with pytest.raises(ValueError, match="job_id cannot be empty"):
            StepFunctionMintingRequest.model_validate(
                {"sourceIdentifiers": ["sierra/1"], "jobId": "   "}
            )

    def test_accepts_window_camel_case(self) -> None:
        request = StepFunctionMintingRequest.model_validate(
            {
                "startTime": "2025-03-25T14:45:00",
                "endTime": "2025-03-25T15:00:00",
                "jobId": "win-001",
            }
        )
        assert request.start_time == datetime(2025, 3, 25, 14, 45, 0)
        assert request.end_time == datetime(2025, 3, 25, 15, 0, 0)

    def test_accepts_window_snake_case(self) -> None:
        request = StepFunctionMintingRequest.model_validate(
            {
                "start_time": "2025-03-25T14:45:00",
                "end_time": "2025-03-25T15:00:00",
                "job_id": "win-002",
            }
        )
        assert request.start_time == datetime(2025, 3, 25, 14, 45, 0)
        assert request.end_time == datetime(2025, 3, 25, 15, 0, 0)


class TestStepFunctionMintingRequestWindowHandling:
    """Tests for window-mode input handling."""

    def test_defaults_start_time_when_only_end_time_given(self) -> None:
        end = datetime(2025, 3, 25, 15, 0, 0)
        request = StepFunctionMintingRequest(end_time=end, job_id="win-003")
        assert request.start_time == end - timedelta(minutes=DEFAULT_WINDOW_MINUTES)

    def test_rejects_start_time_after_end_time(self) -> None:
        with pytest.raises(ValueError, match="start_time must be before end_time"):
            StepFunctionMintingRequest(
                start_time=datetime(2025, 3, 25, 16, 0, 0),
                end_time=datetime(2025, 3, 25, 15, 0, 0),
                job_id="win-005",
            )

    def test_rejects_start_time_without_end_time(self) -> None:
        with pytest.raises(ValueError, match="end_time is required"):
            StepFunctionMintingRequest(
                start_time=datetime(2025, 3, 25, 14, 0, 0),
                job_id="win-006",
            )


class TestStepFunctionMintingRequestModeExclusivity:
    """Tests that exactly one mode (ids / window / full) is selected."""

    def test_ids_mode(self) -> None:
        request = StepFunctionMintingRequest(
            source_identifiers=["sierra/1"], job_id="mode-ids"
        )
        assert request.source_query.mode_label == "identifiers"
        assert request.source_query.source_identifiers == ["sierra/1"]
        assert request.start_time is None
        assert request.end_time is None

    def test_window_mode(self) -> None:
        request = StepFunctionMintingRequest(
            end_time=datetime(2025, 3, 25, 15, 0, 0), job_id="mode-window"
        )
        assert request.source_query.mode_label == "window"
        assert request.source_identifiers is None
        assert request.end_time is not None
        assert request.start_time is not None

    def test_full_reprocess_mode(self) -> None:
        request = StepFunctionMintingRequest(job_id="mode-full")
        assert request.source_query.mode_label == "match_all"
        assert request.source_identifiers is None
        assert request.start_time is None
        assert request.end_time is None

    def test_rejects_ids_with_end_time(self) -> None:
        with pytest.raises(
            ValueError, match="Cannot specify both source_identifiers and a time window"
        ):
            StepFunctionMintingRequest(
                source_identifiers=["sierra/1"],
                end_time=datetime(2025, 3, 25, 15, 0, 0),
                job_id="mode-bad-1",
            )

    def test_rejects_ids_with_start_time(self) -> None:
        with pytest.raises(
            ValueError, match="Cannot specify both source_identifiers and a time window"
        ):
            StepFunctionMintingRequest(
                source_identifiers=["sierra/1"],
                start_time=datetime(2025, 3, 25, 14, 0, 0),
                job_id="mode-bad-2",
            )

    def test_rejects_ids_with_both_times(self) -> None:
        with pytest.raises(
            ValueError, match="Cannot specify both source_identifiers and a time window"
        ):
            StepFunctionMintingRequest(
                source_identifiers=["sierra/1"],
                start_time=datetime(2025, 3, 25, 14, 0, 0),
                end_time=datetime(2025, 3, 25, 15, 0, 0),
                job_id="mode-bad-3",
            )
