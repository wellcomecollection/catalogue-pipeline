from typing import Any
from unittest.mock import patch

from id_minter.models.identifier import SourceId
from id_minter.models.step_events import (
    StepFunctionMintingRequest,
    StepFunctionMintingResponse,
)
from id_minter.steps.id_minter import (
    IdMinterRuntime,
    execute,
    handler,
)
from tests.mocks import MockElasticsearchClient


class FakeResolver:
    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
        return {}

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]:
        return {}


def _runtime() -> IdMinterRuntime:
    return IdMinterRuntime(resolver=FakeResolver())


# ---------------------------------------------------------------------------
# Handler
# ---------------------------------------------------------------------------
@patch(
    "id_minter.steps.id_minter.get_client",
    return_value=MockElasticsearchClient({}, ""),
)
class TestIdMinterHandler:
    def test_execute_returns_empty_response(self, _mock_client: Any) -> None:
        request = StepFunctionMintingRequest(
            source_identifiers=["id1", "id2"],
            job_id="test-job",
        )
        response = execute(request, runtime=_runtime())

        assert isinstance(response, StepFunctionMintingResponse)
        assert response.job_id == "test-job"
        assert response.successes == []
        assert response.failures == []

    def test_handler_returns_response(self, _mock_client: Any) -> None:
        request = StepFunctionMintingRequest(
            source_identifiers=["id1"],
            job_id="test-job",
        )
        response = handler(request, runtime=_runtime())

        assert isinstance(response, StepFunctionMintingResponse)
        assert response.job_id == "test-job"
