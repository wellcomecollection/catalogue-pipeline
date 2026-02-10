

from id_minter.models.step_events import (
    StepFunctionMintingRequest,
    StepFunctionMintingResponse,
)
from id_minter.steps.id_minter import (
    execute,
    handler,
    lambda_handler,
)


# ---------------------------------------------------------------------------
# Handler
# ---------------------------------------------------------------------------
class TestIdMinterHandler:
    def test_execute_returns_empty_response(self) -> None:
        request = StepFunctionMintingRequest(
            source_identifiers=["id1", "id2"],
            job_id="test-job",
        )
        response = execute(request)

        assert isinstance(response, StepFunctionMintingResponse)
        assert response.job_id == "test-job"
        assert response.successes == []
        assert response.failures == []

    def test_handler_returns_response(self) -> None:
        request = StepFunctionMintingRequest(
            source_identifiers=["id1"],
            job_id="test-job",
        )
        response = handler(request)

        assert isinstance(response, StepFunctionMintingResponse)
        assert response.job_id == "test-job"

    def test_lambda_handler_returns_dict(self) -> None:
        event = {
            "source_identifiers": ["id1", "id2"],
            "job_id": "test-job",
        }
        result = lambda_handler(event, None)

        assert isinstance(result, dict)
        assert result["job_id"] == "test-job"
        assert "successes" in result
        assert "failures" in result
