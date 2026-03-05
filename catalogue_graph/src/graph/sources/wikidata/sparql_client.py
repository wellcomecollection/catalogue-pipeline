import os
import threading
import time
import typing

import backoff
import requests
import structlog

from config import WIKIDATA_SPARQL_URL

logger = structlog.get_logger(__name__)

# Wikidata limits the number of parallel queries from a single IP address to 5.
# See: https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual#Query_limits
# However, experimentally, running more than 4 queries in parallel consistently results in '429 Too Many Requests' errors.
SPARQL_MAX_PARALLEL_QUERIES = 4
SPARQL_REQUESTS_BACKOFF_RETRIES = int(os.environ.get("REQUESTS_BACKOFF_RETRIES", "3"))
SPARQL_REQUESTS_BACKOFF_INTERVAL = 10


def on_request_backoff(backoff_details: typing.Any) -> None:
    exception_name = type(backoff_details["exception"]).__name__
    logger.warning("SPARQL request failed, retrying", exception=exception_name)


class WikidataSparqlClient:
    """
    A client class for querying Wikidata via SPARQL queries. Automatically throttles requests (in a thread-safe way)
    so that we do not exceed Wikidata rate limits.
    """

    def __init__(self) -> None:
        self.parallel_query_semaphore = threading.Semaphore(SPARQL_MAX_PARALLEL_QUERIES)
        self.too_many_requests = False
        self.too_many_requests_lock = threading.Lock()

    @staticmethod
    def _get_user_agent_header() -> str:
        """
        Return a User-Agent header value complying with Wikimedia's User-Agent policy:
        https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
        """
        return (
            "WellcomeCollectionCatalogueGraphPipeline/0.1 (https://wellcomecollection.org/; "
            "digital@wellcomecollection.org) wellcome-collection-catalogue-graph/0.1"
        )

    @backoff.on_exception(
        backoff.constant,
        Exception,
        max_tries=SPARQL_REQUESTS_BACKOFF_RETRIES,
        interval=SPARQL_REQUESTS_BACKOFF_INTERVAL,
        on_backoff=on_request_backoff,
    )
    def run_query(self, query: str) -> list[dict]:
        """Runs a query against Wikidata's SPARQL endpoint and returns the results as a list"""

        while True:
            with self.too_many_requests_lock:
                if not self.too_many_requests:
                    break
            time.sleep(2)

        # Use a semaphore to throttle the number of parallel requests
        with self.parallel_query_semaphore:
            r = requests.get(
                WIKIDATA_SPARQL_URL,
                params={"format": "json", "query": query},
                headers={"User-Agent": self._get_user_agent_header()},
            )

        # Even though we limit the number of requests, we might still occasionally get a 429 error.
        # When this happens, set the `too_many_requests` flag to prevent other threads from making new requests
        # and sleep for at least a minute.
        if r.status_code == 429:
            with self.too_many_requests_lock:
                self.too_many_requests = True

            retry_after = int(r.headers["Retry-After"])
            sleep_time = max(60, retry_after)
            logger.warning(
                "Too many SPARQL requests, sleeping",
                retry_after=retry_after,
                sleep_seconds=sleep_time,
            )
            time.sleep(sleep_time)

            with self.too_many_requests_lock:
                self.too_many_requests = False

            return self.run_query(query)
        elif r.status_code != 200:
            raise Exception(r.content)

        results: list[dict] = r.json()["results"]["bindings"]
        return results
