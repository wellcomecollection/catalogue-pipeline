import pytest
from _pytest.monkeypatch import MonkeyPatch

from clients.neptune_client import (
    NEPTUNE_REQUESTS_BACKOFF_RETRIES,
    NeptuneRequestError,
    TransientNeptuneError,
)
from tests.mocks import MOCK_NEPTUNE_ENDPOINT, MockRequest, get_mock_neptune_client

LOADER_URL = f"https://{MOCK_NEPTUNE_ENDPOINT}:8182/loader"


@pytest.fixture(autouse=True)
def _no_backoff_sleep(monkeypatch: MonkeyPatch) -> None:
    # Scoped to the decorator's sleep, so later timing assertions here still work.
    monkeypatch.setattr("backoff._sync.time.sleep", lambda *_a, **_k: None)


def _mock_loader_response(status_code: int, code: str = "SomeException") -> None:
    MockRequest.mock_response(
        method="POST",
        url=LOADER_URL,
        status_code=status_code,
        json_data={"code": code},
        content_bytes=b"{}",
    )


@pytest.mark.parametrize("status_code", [429, 500, 502, 503, 504])
def test_transient_status_raises_transient_error(status_code: int) -> None:
    _mock_loader_response(status_code)
    client = get_mock_neptune_client()

    with pytest.raises(TransientNeptuneError):
        client.initiate_bulk_load(s3_file_uri="s3://bucket/nodes.csv")


@pytest.mark.parametrize("status_code", [400, 403, 404])
def test_permanent_status_raises_plain_request_error(status_code: int) -> None:
    _mock_loader_response(status_code)
    client = get_mock_neptune_client()

    with pytest.raises(NeptuneRequestError) as excinfo:
        client.initiate_bulk_load(s3_file_uri="s3://bucket/nodes.csv")

    assert not isinstance(excinfo.value, TransientNeptuneError)


def test_permanent_status_is_not_retried() -> None:
    _mock_loader_response(400)
    client = get_mock_neptune_client()
    MockRequest.clear_mock_calls()

    with pytest.raises(NeptuneRequestError):
        client.initiate_bulk_load(s3_file_uri="s3://bucket/nodes.csv")

    assert len(MockRequest.calls) == 1


def test_transient_status_is_retried_until_exhausted() -> None:
    _mock_loader_response(500)
    client = get_mock_neptune_client()
    MockRequest.clear_mock_calls()

    with pytest.raises(TransientNeptuneError):
        client.initiate_bulk_load(s3_file_uri="s3://bucket/nodes.csv")

    assert len(MockRequest.calls) == NEPTUNE_REQUESTS_BACKOFF_RETRIES


def test_transient_error_subclasses_request_error() -> None:
    # Callers catching the base type keep working once retries are exhausted.
    assert issubclass(TransientNeptuneError, NeptuneRequestError)


def test_retryable_400_is_transient_despite_its_status() -> None:
    _mock_loader_response(400, code="ConstraintViolationException")
    client = get_mock_neptune_client()

    with pytest.raises(TransientNeptuneError):
        client.initiate_bulk_load(s3_file_uri="s3://bucket/nodes.csv")


def test_status_is_used_when_there_is_no_engine_error_body() -> None:
    MockRequest.mock_response(
        method="POST", url=LOADER_URL, status_code=502, content_bytes=b"<html>"
    )
    client = get_mock_neptune_client()

    with pytest.raises(TransientNeptuneError):
        client.initiate_bulk_load(s3_file_uri="s3://bucket/nodes.csv")
