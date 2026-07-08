import httpx
import pytest

from adapters.extractors.oai_pmh.axiell.webapi_client import (
    AxiellWebApiClient,
    oai_url_to_webapi_url,
)


def _adlib_xml(prirefs: list[str], hits: int) -> bytes:
    records = "".join(f"<record><priref>{p}</priref></record>" for p in prirefs)
    return (
        f'<?xml version="1.0" encoding="UTF-8"?>'
        f"<adlibXML><recordList>{records}</recordList>"
        f"<diagnostic><hits>{hits}</hits></diagnostic></adlibXML>"
    ).encode()


def _client(handler: object, **kwargs: object) -> AxiellWebApiClient:
    transport = httpx.MockTransport(handler)  # type: ignore[arg-type]
    return AxiellWebApiClient(
        base_url="https://host.example/OAI/wwwopac.ashx",
        client=httpx.Client(transport=transport),
        **kwargs,  # type: ignore[arg-type]
    )


def test_oai_url_to_webapi_url_derives_wwwopac() -> None:
    assert (
        oai_url_to_webapi_url("https://host.example/OAI/oai.ashx?verb=Identify")
        == "https://host.example/OAI/wwwopac.ashx"
    )


def test_oai_url_to_webapi_url_rejects_unexpected_url() -> None:
    with pytest.raises(ValueError, match="oai.ashx"):
        oai_url_to_webapi_url("https://host.example/something-else")


def test_count_reads_hits_from_diagnostic() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.params["search"] == "all"
        return httpx.Response(200, content=_adlib_xml([], hits=222040))

    assert _client(handler).count() == 222040


def test_enumerate_ids_pages_statelessly_until_hits_reached() -> None:
    pages = {1: ["1", "2"], 3: ["3"], 5: []}

    def handler(request: httpx.Request) -> httpx.Response:
        startfrom = int(request.url.params["startfrom"])
        return httpx.Response(200, content=_adlib_xml(pages[startfrom], hits=3))

    ids = list(_client(handler, page_size=2).enumerate_ids())
    assert ids == ["collect:1", "collect:2", "collect:3"]


def test_enumerate_ids_stops_on_empty_page() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        startfrom = int(request.url.params["startfrom"])
        prirefs = ["1"] if startfrom == 1 else []
        # hits deliberately larger than returned so the empty page is what stops it
        return httpx.Response(200, content=_adlib_xml(prirefs, hits=999))

    ids = list(_client(handler, page_size=1).enumerate_ids())
    assert ids == ["collect:1"]


def test_empty_body_raises() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"")

    with pytest.raises(ValueError, match="Empty WebAPI response"):
        _client(handler).count()


def test_enumerate_ids_caps_pages_when_startfrom_ignored() -> None:
    # A server that ignores startfrom and reports no total returns a non-empty
    # page forever; the absolute page cap is the only thing that stops it.
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            content=(
                b'<?xml version="1.0"?><adlibXML><recordList>'
                b"<record><priref>1</priref></record>"
                b"</recordList></adlibXML>"  # no <diagnostic><hits>
            ),
        )

    with pytest.raises(RuntimeError, match="exceeded"):
        list(_client(handler, page_size=1, max_pages=3).enumerate_ids())
