import json
from collections.abc import Callable
from typing import Any

import httpx
import pytest

from probes import handler, vendors

CREDS = {
    "sierra": {"url": "https://sierra.test/v6", "key": "k", "secret": "s"},
    "axiell_oai": {"url": "https://axiell.test/oai", "token": "t"},
    "folio_oai": {"url": "https://folio.test/oai", "token": "t"},
    "folio_okapi": {
        "url": "https://okapi.test",
        "tenant": "wellcome",
        "username": "u",
        "password": "p",
    },
    "ebsco_ftp": {
        "server": "ftp.test",
        "username": "u",
        "password": "p",
        "remote_dir": "/out",
    },
}

Handler = Callable[[httpx.Request], httpx.Response]


@pytest.fixture(autouse=True)
def creds(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        handler,
        "get_ssm_parameter",
        lambda name: json.dumps(CREDS[name.rsplit("/", 1)[-1]]),
    )
    monkeypatch.setenv("FOLIO_PROBE_INSTANCE_HRID", "in00000000001")


def _run(vendor: str, respond: Handler) -> bool:
    return handler.run(
        vendor, client=httpx.Client(transport=httpx.MockTransport(respond))
    )


def _records(capsys: pytest.CaptureFixture[str]) -> list[dict[str, Any]]:
    lines = capsys.readouterr().out.splitlines()
    return [json.loads(line) for line in lines if line.startswith('{"_aws"')]


def _metrics(capsys: pytest.CaptureFixture[str]) -> list[dict[str, Any]]:
    """Step Duration records only, without the per-run RunSuccess record."""
    return [r for r in _records(capsys) if "Step" in r]


def _summary(metrics: list[dict[str, Any]]) -> list[tuple[str, str, str]]:
    return [(m["Vendor"], m["Step"], m["Outcome"]) for m in metrics]


def test_emits_one_embedded_metric_per_step(capsys: pytest.CaptureFixture[str]) -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/token"):
            return httpx.Response(200, json={"access_token": "abc"})
        assert request.headers["Authorization"] == "Bearer abc"
        return httpx.Response(200, json={"entries": []})

    assert _run("sierra", respond)

    metrics = _metrics(capsys)
    assert _summary(metrics) == [("sierra", "auth", "ok"), ("sierra", "read", "ok")]
    emf = metrics[0]["_aws"]["CloudWatchMetrics"][0]
    assert emf["Namespace"] == "third_party_probes"
    assert emf["Dimensions"] == [["Vendor", "Step", "Outcome"]]
    assert emf["Metrics"] == [{"Name": "Duration", "Unit": "Milliseconds"}]
    assert isinstance(metrics[0]["Duration"], float)


def test_sierra_empty_result_404_is_ok(capsys: pytest.CaptureFixture[str]) -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/token"):
            return httpx.Response(200, json={"access_token": "abc"})
        return httpx.Response(404, json={"httpStatus": 404})

    assert _run("sierra", respond)
    assert _summary(_metrics(capsys))[-1] == ("sierra", "read", "ok")


def test_timeout_is_recorded_and_skips_later_steps(
    capsys: pytest.CaptureFixture[str],
) -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("handshake timed out", request=request)

    assert not _run("sierra", respond)
    assert _summary(_metrics(capsys)) == [("sierra", "auth", "timeout")]


def test_http_error_records_status_code(capsys: pytest.CaptureFixture[str]) -> None:
    assert not _run("axiell_oai", lambda request: httpx.Response(503))

    [metric] = _metrics(capsys)
    assert (metric["Step"], metric["Outcome"], metric["status_code"]) == (
        "identify",
        "http_error",
        503,
    )


@pytest.mark.parametrize(
    ("vendor", "header", "list_params"),
    [
        ("axiell_oai", "Token", {"metadataPrefix": "oai_marcxml", "set": "collect"}),
        ("folio_oai", "Authorization", {"metadataPrefix": "marc21_withholdings"}),
    ],
)
def test_oai_probes(
    vendor: str,
    header: str,
    list_params: dict[str, str],
    capsys: pytest.CaptureFixture[str],
) -> None:
    requests: list[httpx.Request] = []

    def respond(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, text="<OAI-PMH/>")

    assert _run(vendor, respond)
    assert [r.url.params["verb"] for r in requests] == ["Identify", "ListIdentifiers"]
    assert all(r.headers[header] == "t" for r in requests)
    assert list_params.items() <= dict(requests[1].url.params).items()
    assert "from" in requests[1].url.params
    assert _summary(_metrics(capsys)) == [
        (vendor, "identify", "ok"),
        (vendor, "read", "ok"),
    ]


def test_folio_okapi_searches_by_hrid(capsys: pytest.CaptureFixture[str]) -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/authn/login-with-expiry":
            assert request.headers["x-okapi-tenant"] == "wellcome"
            return httpx.Response(
                201, headers={"set-cookie": "folioAccessToken=tok; Path=/"}
            )
        assert request.headers["x-okapi-token"] == "tok"
        assert request.url.params["query"] == 'hrid=="in00000000001"'
        return httpx.Response(200, json={"instances": [], "totalRecords": 0})

    assert _run("folio_okapi", respond)
    assert _summary(_metrics(capsys)) == [
        ("folio_okapi", "auth", "ok"),
        ("folio_okapi", "read", "ok"),
    ]


class FakeFTP:
    fail_login = False
    instances: list["FakeFTP"] = []

    def __init__(self, timeout: int) -> None:
        self.calls: list[str] = []
        FakeFTP.instances.append(self)

    def connect(self, server: str) -> None:
        self.calls.append("connect")

    def login(self, user: str, password: str) -> None:
        if self.fail_login:
            raise TimeoutError("timed out")

    def cwd(self, path: str) -> None:
        pass

    def nlst(self) -> list[str]:
        return ["a.xml"]

    def quit(self) -> None:
        self.calls.append("quit")

    def close(self) -> None:
        self.calls.append("close")


def test_ebsco_ftp(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr(vendors, "FTP", FakeFTP)
    assert _run("ebsco_ftp", lambda request: httpx.Response(500))
    assert _summary(_metrics(capsys)) == [
        ("ebsco_ftp", "auth", "ok"),
        ("ebsco_ftp", "read", "ok"),
    ]
    assert FakeFTP.instances[-1].calls == ["connect", "quit", "close"]


def test_ebsco_ftp_closes_connection_when_login_fails(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr(vendors, "FTP", FakeFTP)
    monkeypatch.setattr(FakeFTP, "fail_login", True)
    assert not _run("ebsco_ftp", lambda request: httpx.Response(500))
    assert _summary(_metrics(capsys)) == [("ebsco_ftp", "auth", "timeout")]
    assert FakeFTP.instances[-1].calls == ["connect", "close"]


def test_unfilled_credentials_are_recorded_as_setup_error(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr(handler, "get_ssm_parameter", lambda name: "placeholder")
    assert not _run("sierra", lambda request: httpx.Response(200))
    assert _summary(_metrics(capsys)) == [("sierra", "setup", "error")]


def test_incomplete_credentials_are_recorded_as_setup_error(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr(handler, "get_ssm_parameter", lambda name: "{}")
    assert not _run("sierra", lambda request: httpx.Response(200))
    [metric] = _metrics(capsys)
    assert (metric["Step"], metric["Outcome"]) == ("setup", "error")
    assert "url, key, secret" in metric["error"]


@pytest.mark.parametrize(
    "error_xml",
    [
        '<OAI-PMH><error code="badArgument"/></OAI-PMH>',
        "<OAI-PMH><error code='badArgument'/></OAI-PMH>",
        '<oai:OAI-PMH xmlns:oai="http://www.openarchives.org/OAI/2.0/">'
        '<oai:error code="badArgument"/></oai:OAI-PMH>',
    ],
)
def test_oai_error_in_200_is_an_error(
    error_xml: str, capsys: pytest.CaptureFixture[str]
) -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        if request.url.params["verb"] == "Identify":
            return httpx.Response(200, text="<OAI-PMH/>")
        return httpx.Response(200, text=error_xml)

    assert not _run("folio_oai", respond)
    [_, read] = _metrics(capsys)
    assert (read["Step"], read["Outcome"]) == ("read", "error")
    assert "badArgument" in read["error"]


def test_oai_non_xml_200_is_an_error(capsys: pytest.CaptureFixture[str]) -> None:
    html = "<html><body><p>Down for maintenance<br></body></html>"
    assert not _run("axiell_oai", lambda request: httpx.Response(200, text=html))
    assert _summary(_metrics(capsys)) == [("axiell_oai", "identify", "error")]


def test_oai_no_records_match_is_ok(capsys: pytest.CaptureFixture[str]) -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200, text='<OAI-PMH><error code="noRecordsMatch"/></OAI-PMH>'
        )

    assert _run("folio_oai", respond)
    assert _summary(_metrics(capsys))[-1] == ("folio_oai", "read", "ok")


@pytest.mark.parametrize(
    ("respond", "ssm_value", "expected"),
    [
        (lambda request: httpx.Response(200, text="<OAI-PMH/>"), None, 1),
        (lambda request: httpx.Response(503), None, 0),
        (lambda request: httpx.Response(200), "placeholder", 0),
    ],
    ids=["ok", "step-failed", "setup-failed"],
)
def test_each_run_emits_one_run_success_record(
    respond: Handler,
    ssm_value: str | None,
    expected: int,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    if ssm_value is not None:
        monkeypatch.setattr(handler, "get_ssm_parameter", lambda name: ssm_value)

    _run("folio_oai", respond)

    [run] = [r for r in _records(capsys) if "RunSuccess" in r]
    assert (run["Vendor"], run["RunSuccess"]) == ("folio_oai", expected)
    emf = run["_aws"]["CloudWatchMetrics"][0]
    assert emf["Dimensions"] == [["Vendor"]]
    assert emf["Metrics"] == [{"Name": "RunSuccess", "Unit": "None"}]
