"""One probe per vendor: an auth step and one small read, single attempt each.

These deliberately bypass the adapter clients, whose retries and token caching
would hide the slow and failed calls we want to measure.
"""

import os
import re
from datetime import UTC, datetime, timedelta
from ftplib import FTP

import httpx

from probes.emf import Timer

# Matches the adapters' OAI read timeout, so a probe timeout means the adapter would fail too.
TIMEOUT_SECONDS = 60
CONNECT_TIMEOUT_SECONDS = 10


def _an_hour_ago() -> datetime:
    return datetime.now(UTC) - timedelta(hours=1)


def _iso(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def sierra(creds: dict, client: httpx.Client, timed: Timer) -> None:
    url = creds["url"].rstrip("/")

    with timed("auth"):
        resp = client.post(f"{url}/token", auth=(creds["key"], creds["secret"]))
        resp.raise_for_status()
        token = resp.json()["access_token"]

    with timed("read"):
        resp = client.get(
            f"{url}/bibs",
            params={
                "updatedDate": f"[{_iso(_an_hour_ago())},{_iso(datetime.now(UTC))}]",
                "limit": 1,
                "fields": "id",
            },
            headers={"Authorization": f"Bearer {token}"},
        )
        # Sierra answers an empty result set with 404.
        if resp.status_code != 404:
            resp.raise_for_status()


class OaiError(Exception):
    pass


def _check_oai(resp: httpx.Response) -> None:
    # OAI-PMH reports protocol errors inside a 200; an empty window is fine.
    resp.raise_for_status()
    codes = set(re.findall(r'<error code="([^"]+)"', resp.text)) - {"noRecordsMatch"}
    if codes:
        raise OaiError(", ".join(sorted(codes)))


def _oai(
    creds: dict,
    client: httpx.Client,
    timed: Timer,
    *,
    auth_header: str,
    list_params: dict,
) -> None:
    headers = {auth_header: creds["token"]}

    with timed("identify"):
        _check_oai(
            client.get(creds["url"], params={"verb": "Identify"}, headers=headers)
        )

    with timed("read"):
        _check_oai(
            client.get(
                creds["url"],
                params={
                    "verb": "ListIdentifiers",
                    "from": _iso(_an_hour_ago()),
                    **list_params,
                },
                headers=headers,
            )
        )


def axiell_oai(creds: dict, client: httpx.Client, timed: Timer) -> None:
    _oai(
        creds,
        client,
        timed,
        auth_header="Token",
        list_params={"metadataPrefix": "oai_marcxml", "set": "collect"},
    )


def folio_oai(creds: dict, client: httpx.Client, timed: Timer) -> None:
    _oai(
        creds,
        client,
        timed,
        auth_header="Authorization",
        list_params={"metadataPrefix": "marc21_withholdings"},
    )


def folio_okapi(creds: dict, client: httpx.Client, timed: Timer) -> None:
    url = creds["url"].rstrip("/")
    tenant = {"x-okapi-tenant": creds["tenant"]}

    with timed("auth"):
        resp = client.post(
            f"{url}/authn/login-with-expiry",
            json={"username": creds["username"], "password": creds["password"]},
            headers=tenant,
        )
        resp.raise_for_status()
        token = resp.cookies["folioAccessToken"]

    with timed("read"):
        hrid = os.environ["FOLIO_PROBE_INSTANCE_HRID"]
        client.get(
            f"{url}/inventory/instances",
            params={"query": f'hrid=="{hrid}"', "limit": 1},
            headers={**tenant, "x-okapi-token": token},
        ).raise_for_status()


def ebsco_ftp(creds: dict, client: httpx.Client, timed: Timer) -> None:
    ftp = FTP(timeout=TIMEOUT_SECONDS)
    try:
        with timed("auth"):
            ftp.connect(creds["server"])
            ftp.login(creds["username"], creds["password"])
            ftp.cwd(creds["remote_dir"])

        with timed("read"):
            ftp.nlst()

        ftp.quit()
    finally:
        ftp.close()
