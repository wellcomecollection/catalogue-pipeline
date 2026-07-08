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
# Absolute page ceiling. At the default page size this is ~1 billion records,
# far beyond any real database, so it only ever trips on a server that ignores
# ``startfrom`` and never drains (which would otherwise loop until timeout).
DEFAULT_MAX_PAGES = 1_000_000


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
        max_pages: int = DEFAULT_MAX_PAGES,
    ) -> None:
        self._base_url = base_url
        self._client = client
        self._database = database
        self._page_size = page_size
        self._max_pages = max_pages

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
        """Yield every record id as ``<namespace>:<priref>``, paging statelessly.

        Each page is an independent request (``startfrom`` offset), so a retry of
        one page never invalidates the others. Terminates on ``seen >= total`` or
        an empty page; ``max_pages`` is an absolute backstop against a server that
        ignores ``startfrom`` and never drains (which matters most when the
        response carries no ``hits`` total to bound the loop).
        """
        startfrom = 1
        total: int | None = None
        seen = 0
        pages = 0
        while True:
            if pages >= self._max_pages:
                raise RuntimeError(
                    f"WebAPI enumeration exceeded {self._max_pages} pages without "
                    f"draining (seen={seen}, total={total}); startfrom may be "
                    f"ignored server-side"
                )
            root = self._search(
                fields="priref", limit=self._page_size, startfrom=startfrom
            )
            pages += 1
            if total is None:
                hits = root.findtext(".//diagnostic/hits")
                total = int(hits) if hits is not None else None
            prirefs = [p.text for p in root.findall(".//record/priref") if p.text]
            if not prirefs:
                break
            for priref in prirefs:
                yield f"{namespace}:{priref}"
            seen += len(prirefs)
            startfrom += self._page_size
            if total is not None and seen >= total:
                break
        logger.info("Enumerated WebAPI ids", database=self._database, count=seen)
