from __future__ import annotations

import httpx
import pytest

from adapters.oai_pmh.http_client import OAIPMHHTTPClient


class TestOAIPMHHTTPClient:
    def test_adds_auth_header_to_requests(self) -> None:
        client = OAIPMHHTTPClient(token="test-token", auth_header="Authorization")

        request = client.build_request("GET", "https://example.com/oai")

        assert request.headers.get("Authorization") == "test-token"

    def test_uses_custom_auth_header_name(self) -> None:
        client = OAIPMHHTTPClient(token="test-token", auth_header="Token")

        request = client.build_request("GET", "https://example.com/oai")

        assert request.headers.get("Token") == "test-token"
        assert request.headers.get("Authorization") is None

    def test_preserves_other_headers(self) -> None:
        client = OAIPMHHTTPClient(token="test-token", auth_header="Authorization")

        request = client.build_request(
            "GET",
            "https://example.com/oai",
            headers={"Accept": "application/xml", "Custom-Header": "value"},
        )

        assert request.headers.get("Authorization") == "test-token"
        assert request.headers.get("Accept") == "application/xml"
        assert request.headers.get("Custom-Header") == "value"

    def test_does_not_override_explicit_auth_header(self) -> None:
        client = OAIPMHHTTPClient(token="default-token", auth_header="Authorization")

        request = client.build_request(
            "GET",
            "https://example.com/oai",
            headers={"Authorization": "custom-auth"},
        )

        # setdefault should keep the existing value
        assert request.headers.get("Authorization") == "custom-auth"

    def test_inherits_httpx_client(self) -> None:
        client = OAIPMHHTTPClient(token="test-token")

        assert isinstance(client, httpx.Client)
        assert hasattr(client, "get")
        assert hasattr(client, "post")

    def test_uses_custom_timeout(self) -> None:
        timeout = httpx.Timeout(5.0, read=30.0)
        client = OAIPMHHTTPClient(token="test-token", timeout=timeout)

        assert client.timeout == timeout

    @pytest.mark.parametrize(
        "header_name",
        ["Token", "Authorization", "X-API-Key", "Bearer"],
    )
    def test_supports_various_header_names(self, header_name: str) -> None:
        client = OAIPMHHTTPClient(token="test-token", auth_header=header_name)

        request = client.build_request("GET", "https://example.com/oai")

        assert request.headers.get(header_name) == "test-token"
