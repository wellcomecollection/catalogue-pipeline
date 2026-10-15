import json

import httpx
import pytest

from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)


def _client(handler: object, **kwargs: object) -> FolioInventoryClient:
    transport = httpx.MockTransport(handler)  # type: ignore[arg-type]
    return FolioInventoryClient(
        base_url="https://inventory.example/",
        client=httpx.Client(transport=transport),
        **kwargs,  # type: ignore[arg-type]
    )


def test_items_and_holdings_posts_instance_ids_and_parses_response() -> None:
    seen_requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_requests.append(request)
        return httpx.Response(
            200,
            json=[
                {"instanceId": "i-1", "items": [{"id": "item-1"}]},
                {"instanceId": "i-2", "items": [{"id": "item-2"}]},
            ],
        )

    client = _client(handler)
    result = client.items_and_holdings(["i-1", "i-2"])

    assert [r.instance_id for r in result] == ["i-1", "i-2"]
    assert result[0].data["items"][0]["id"] == "item-1"

    assert len(seen_requests) == 1
    request = seen_requests[0]
    assert request.method == "POST"
    assert str(request.url).endswith("/inventory-hierarchy/items-and-holdings")
    body = json.loads(request.content)
    assert body["instanceIds"] == ["i-1", "i-2"]
    assert body["skipSuppressedFromDiscoveryRecords"] is False


def test_items_and_holdings_batches_requests() -> None:
    batches: list[list[str]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        ids = json.loads(request.content)["instanceIds"]
        batches.append(ids)
        return httpx.Response(200, json=[{"instanceId": i} for i in ids])

    client = _client(handler, batch_size=2)
    result = client.items_and_holdings(["a", "b", "c", "d", "e"])

    assert batches == [["a", "b"], ["c", "d"], ["e"]]
    assert [r.instance_id for r in result] == ["a", "b", "c", "d", "e"]


def test_items_and_holdings_deduplicates_ids() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        ids = json.loads(request.content)["instanceIds"]
        assert ids == ["a", "b"]
        return httpx.Response(200, json=[])

    client = _client(handler)
    client.items_and_holdings(["a", "b", "a", "", "b"])


def test_items_and_holdings_parses_ndjson() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        body = "\n".join(
            json.dumps(r) for r in [{"instanceId": "i-1"}, {"instanceId": "i-2"}]
        )
        return httpx.Response(200, text=body)

    client = _client(handler)
    result = client.items_and_holdings(["i-1", "i-2"])
    assert [r.instance_id for r in result] == ["i-1", "i-2"]


def test_items_and_holdings_parses_concatenated_json_stream() -> None:
    """The live endpoint streams concatenated objects with no array or newlines."""

    def handler(request: httpx.Request) -> httpx.Response:
        body = (
            '{"instanceId":"i-1","holdings":[],"items":[]}'
            '{"instanceId":"i-2","holdings":[],'
            '"items":[{"id":"item-9b59","materialType":"book"}]}'
        )
        return httpx.Response(200, text=body)

    client = _client(handler)
    result = client.items_and_holdings(["i-1", "i-2"])
    assert [r.data for r in result] == [
        {"instanceId": "i-1", "holdings": [], "items": []},
        {
            "instanceId": "i-2",
            "holdings": [],
            "items": [{"id": "item-9b59", "materialType": "book"}],
        },
    ]


def test_items_and_holdings_empty_input_makes_no_request() -> None:
    def handler(request: httpx.Request) -> httpx.Response:  # pragma: no cover
        raise AssertionError("should not be called")

    client = _client(handler)
    assert client.items_and_holdings([]) == []


def test_items_and_holdings_fails_the_batch_when_an_item_has_no_id() -> None:
    """An item with no id cannot mint a stable identifier, so the fetch fails."""

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json=[
                {"instanceId": "i-1", "items": [{"id": "item-1"}]},
                {"instanceId": "i-2", "items": [{"barcode": "123"}]},
            ],
        )

    with pytest.raises(ValueError, match="missing an id"):
        _client(handler).items_and_holdings(["i-1", "i-2"])
