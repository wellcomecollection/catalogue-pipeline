"""Client for the Axiell WebAPI (``wwwopac.ashx``).

The Axiell OAI-PMH endpoint (``oai.ashx``) is one handler inside a WebAPI
application that also serves ``wwwopac.ashx`` on the same host with the same
``Token`` header. Unlike OAI-PMH it supports stateless ``startfrom`` paging with
no resumption tokens, so it is a reliable source of truth for reconciliation: a
full id enumeration cannot be derailed by one dropped page.

Only read-only ``search`` requests are used. The URL is derived from the OAI
endpoint (``oai.ashx`` -> ``wwwopac.ashx``) and reuses the Axiell Token client,
so no new credentials are needed.
"""

from __future__ import annotations

from collections.abc import Iterator

import httpx
import structlog
from lxml import etree

logger = structlog.get_logger(__name__)

DEFAULT_DATABASE = "collect"
DEFAULT_PAGE_SIZE = 1000


def oai_url_to_webapi_url(oai_endpoint: str) -> str:
    """Derive the wwwopac.ashx URL from the oai.ashx URL (same application)."""
    base = oai_endpoint.split("?", 1)[0]
    if "/oai.ashx" not in base:
        raise ValueError(f"Cannot derive WebAPI URL: expected '/oai.ashx' in {base!r}")
    return base.replace("/oai.ashx", "/wwwopac.ashx")


class AxiellWebApiClient:
    """Read-only client for Axiell ``wwwopac.ashx`` search requests.

    Args:
        base_url: The full ``wwwopac.ashx`` URL.
        client: An ``httpx.Client`` carrying the Axiell ``Token`` header. Injected
            so it can be mocked in tests.
        database: The Axiell database to query (default ``collect``).
        page_size: Records per page when enumerating (default 1000).
    """

    def __init__(
        self,
        *,
        base_url: str,
        client: httpx.Client,
        database: str = DEFAULT_DATABASE,
        page_size: int = DEFAULT_PAGE_SIZE,
    ) -> None:
        self._base_url = base_url
        self._client = client
        self._database = database
        self._page_size = page_size

    def _search(self, *, fields: str, limit: int, startfrom: int) -> etree._Element:
        response = self._client.get(
            self._base_url,
            params={
                "database": self._database,
                "search": "all",
                "fields": fields,
                "limit": str(limit),
                "startfrom": str(startfrom),
            },
        )
        response.raise_for_status()
        if not response.content:
            raise ValueError(
                f"Empty WebAPI response for startfrom={startfrom} "
                f"(database={self._database})"
            )
        return etree.fromstring(response.content)

    def count(self) -> int:
        """Return the total number of records in the database.

        Reads ``<diagnostic><hits>`` from a minimal search that returns no rows.
        """
        root = self._search(fields="priref", limit=1, startfrom=1)
        hits = root.findtext(".//diagnostic/hits")
        if hits is None:
            raise ValueError("WebAPI response did not contain a <hits> count")
        return int(hits)

    def enumerate_ids(self, *, namespace: str = DEFAULT_DATABASE) -> Iterator[str]:
        """Yield every distinct record id as ``<namespace>:<priref>``.

        Each page is an independent ``startfrom`` request, so a retry of one page
        never invalidates the others. The expected total comes from :meth:`count`,
        which requires ``<diagnostic><hits>`` and so fails loudly if the server
        stops reporting it.

        Ids already yielded are tracked, so a server that ignores ``startfrom``
        and repeats a page terminates immediately rather than re-yielding the same
        records until the total is nominally reached. Enumeration is only
        considered complete when the distinct id count matches the expected total.
        """
        total = self.count()
        seen_ids: set[str] = set()
        startfrom = 1
        while len(seen_ids) < total:
            root = self._search(
                fields="priref", limit=self._page_size, startfrom=startfrom
            )
            prirefs = [p.text for p in root.findall(".//record/priref") if p.text]
            if not prirefs:
                break
            new_ids = []
            for priref in prirefs:
                record_id = f"{namespace}:{priref}"
                if record_id not in seen_ids:
                    new_ids.append(record_id)
            if not new_ids:
                break
            seen_ids.update(new_ids)
            yield from new_ids
            startfrom += self._page_size
        if len(seen_ids) != total:
            raise RuntimeError(
                f"WebAPI enumeration yielded {len(seen_ids)} distinct ids but the "
                f"database reports {total} (database={self._database}); paging may "
                f"be faulty server-side"
            )
        logger.info(
            "Enumerated WebAPI ids", database=self._database, count=len(seen_ids)
        )
