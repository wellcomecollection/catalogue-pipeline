"""Tests for StepFunctionMintingRequest camelCase / snake_case handling."""

import pytest

from id_minter.models.step_events import StepFunctionMintingRequest


class TestStepFunctionMintingRequestCaseHandling:
    def test_accepts_camel_case_input(self) -> None:
        request = StepFunctionMintingRequest.model_validate(
            {"sourceIdentifiers": ["sierra/1", "sierra/2"], "jobId": "job-001"}
        )
        assert request.source_identifiers == ["sierra/1", "sierra/2"]
        assert request.job_id == "job-001"

    def test_accepts_snake_case_input(self) -> None:
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
