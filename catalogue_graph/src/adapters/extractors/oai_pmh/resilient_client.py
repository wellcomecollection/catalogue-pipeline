"""Resilient OAI-PMH client with retries for empty responses and transport errors.

Some OAI-PMH servers (notably the Axiell Adlib endpoint) intermittently return
HTTP 200 with an empty body, which surfaces from the client library as
``lxml.etree.XMLSyntaxError("Document is empty")``. Whether such a failure is
retryable depends on the type of page being requested:

- First pages (identified by ``metadataPrefix`` etc.) are plain queries and can
  always be retried safely.
- Continuation pages (identified by a ``resumptionToken`` parameter) cannot:
  resumption tokens are single-use and session-bound, so once a token-page
  request has been answered (even with an empty body) the token is burned and
  retrying it never succeeds. The only recovery is the caller's window-level
  retry, which starts a fresh first-page query.

This module overrides ``OAIClient._request``, a private API of the pinned
``oai_pmh_client`` dependency. The override must be re-verified whenever the
dependency version is bumped (currently pinned to v1.0.1).
"""

from __future__ import annotations

import time
from typing import Any

import httpx
import structlog
from lxml import etree
from oai_pmh_client.client import OAIClient

logger = structlog.get_logger(__name__)

# A transport error may occur before the request reaches the server, in which
# case the resumption token is still valid. But it may also occur after the
# server has answered (e.g. the connection dropped mid-response), in which case
# the token is burned. We therefore retry token pages once (to cover the
# request-never-arrived case) and then give up, rather than exhausting the full
# retry ladder against a token that is most likely already consumed.
TOKEN_PAGE_TRANSPORT_RETRIES = 1


class ResilientOAIClient(OAIClient):
    """OAIClient subclass that retries transient request failures.

    Retry behaviour by failure mode:

    - Empty body (``XMLSyntaxError``): retried up to ``empty_body_retries``
      times for first pages; re-raised immediately for token pages (the token
      is burned, see module docstring).
    - Transport errors (``httpx.TransportError``) and 5xx responses
      (``httpx.HTTPStatusError``): retried up to ``empty_body_retries`` times
      for first pages and once for token pages.
    - 4xx responses and OAI protocol errors are never retried.

    Retries use exponential backoff:
    ``min(empty_body_backoff_factor * 2**(attempt - 1), empty_body_backoff_max)``.
    """

    def __init__(
        self,
        base_url: str,
        *,
        client: httpx.Client,
        empty_body_retries: int = 3,
        empty_body_backoff_factor: float = 2.0,
        empty_body_backoff_max: float = 30.0,
        **kwargs: Any,
    ) -> None:
        super().__init__(base_url, client=client, **kwargs)
        self.empty_body_retries = max(0, empty_body_retries)
        self.empty_body_backoff_factor = max(0.0, empty_body_backoff_factor)
        self.empty_body_backoff_max = max(0.0, empty_body_backoff_max)

    def _request(self, verb: str, **kwargs: Any) -> etree._Element:
        """Make an OAI-PMH request, retrying transient failures where safe."""
        is_token_page = "resumptionToken" in kwargs
        attempt = 0

        while True:
            attempt += 1
            try:
                # The untyped base method returns the parsed XML root element.
                result: etree._Element = super()._request(verb, **kwargs)
                return result
            except etree.XMLSyntaxError as error:
                if is_token_page:
                    # The token was consumed by the failed request and can
                    # never be replayed; only a window-level retry (which
                    # issues a fresh first-page query) can recover.
                    logger.warning(
                        "Empty/invalid OAI response on a resumption token page; "
                        "not retrying (token is burned)",
                        verb=verb,
                        error=repr(error),
                    )
                    raise
                if attempt > self.empty_body_retries:
                    raise
                self._backoff(attempt, verb, error, is_token_page=is_token_page)
            except httpx.HTTPStatusError as error:
                if error.response.status_code < 500:
                    raise
                if attempt > self._max_retries_for(is_token_page):
                    raise
                self._backoff(attempt, verb, error, is_token_page=is_token_page)
            except httpx.TransportError as error:
                # Note: the base client's _send_with_retries already retries
                # timeout exceptions (a TransportError subset) internally, up
                # to max_request_retries, so a timeout only surfaces here once
                # those are exhausted. Raising OAI_MAX_RETRIES therefore stacks
                # multiplicatively with this outer ladder for timeouts.
                if attempt > self._max_retries_for(is_token_page):
                    raise
                self._backoff(attempt, verb, error, is_token_page=is_token_page)

    def _max_retries_for(self, is_token_page: bool) -> int:
        if is_token_page:
            return min(TOKEN_PAGE_TRANSPORT_RETRIES, self.empty_body_retries)
        return self.empty_body_retries

    def _backoff(
        self, attempt: int, verb: str, error: Exception, *, is_token_page: bool
    ) -> None:
        delay = min(
            self.empty_body_backoff_factor * (2 ** (attempt - 1)),
            self.empty_body_backoff_max,
        )
        logger.warning(
            "Retrying OAI request after transient failure",
            verb=verb,
            attempt=attempt,
            max_retries=self._max_retries_for(is_token_page),
            delay_seconds=delay,
            is_token_page=is_token_page,
            error=repr(error),
        )
        if delay > 0:
            time.sleep(delay)
