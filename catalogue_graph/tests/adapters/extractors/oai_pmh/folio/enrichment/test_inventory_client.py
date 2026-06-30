import json

import httpx

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


def test_enriched_instances_posts_instance_ids_and_parses_response() -> None:
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
    result = client.enriched_instances(["i-1", "i-2"])

    assert [i.instance_id for i in result] == ["i-1", "i-2"]
    assert result[0].items[0].id == "item-1"

    assert len(seen_requests) == 1
    request = seen_requests[0]
    assert request.method == "POST"
    assert str(request.url).endswith("/oai-pmh-view/enrichedInstances")
    body = json.loads(request.content)
    assert body["instanceIds"] == ["i-1", "i-2"]
    assert body["skipSuppressedFromDiscoveryRecords"] is False


def test_enriched_instances_batches_requests() -> None:
    batches: list[list[str]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        ids = json.loads(request.content)["instanceIds"]
        batches.append(ids)
        return httpx.Response(200, json=[{"instanceId": i} for i in ids])

    client = _client(handler, batch_size=2)
    result = client.enriched_instances(["a", "b", "c", "d", "e"])

    assert batches == [["a", "b"], ["c", "d"], ["e"]]
    assert [i.instance_id for i in result] == ["a", "b", "c", "d", "e"]


def test_enriched_instances_deduplicates_ids() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        ids = json.loads(request.content)["instanceIds"]
        assert ids == ["a", "b"]
        return httpx.Response(200, json=[])

    client = _client(handler)
    client.enriched_instances(["a", "b", "a", "", "b"])


def test_enriched_instances_parses_ndjson() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        body = "\n".join(
            json.dumps(r) for r in [{"instanceId": "i-1"}, {"instanceId": "i-2"}]
        )
        return httpx.Response(200, text=body)

    client = _client(handler)
    result = client.enriched_instances(["i-1", "i-2"])
    assert [i.instance_id for i in result] == ["i-1", "i-2"]


def test_enriched_instances_empty_input_makes_no_request() -> None:
    def handler(request: httpx.Request) -> httpx.Response:  # pragma: no cover
        raise AssertionError("should not be called")

    client = _client(handler)
    assert client.enriched_instances([]) == []
