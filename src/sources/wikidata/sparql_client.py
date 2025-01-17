import requests
import time

# Wikidata limits the number of parallel queries from a single IP address to 5.
# See: https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual#Query_limits
# However, experimentally, running more than 4 queries in parallel consistently results '429 Too Many Requests' errors.
MAX_PARALLEL_SPARQL_QUERIES = 4


class WikidataSparqlClient:
    """
    A client class for querying Wikidata via SPARQL queries. Automatically throttles requests so that we do not exceed
    Wikidata rate limits.
    """

    parallel_query_count = 0
    too_many_requests = False

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

    def run_query(self, query: str) -> list[dict]:
        """Runs a query against Wikidata's SPARQL endpoint and returns the results as a list"""

        # Make sure we don't exceed the rate limit.
        while (
            self.parallel_query_count >= MAX_PARALLEL_SPARQL_QUERIES
            or self.too_many_requests
        ):
            time.sleep(2)

        self.parallel_query_count += 1
        r = requests.get(
            "https://query.wikidata.org/sparql",
            params={"format": "json", "query": query},
            headers={"User-Agent": self._get_user_agent_header()},
        )
        self.parallel_query_count -= 1

        if r.status_code == 429:
            self.too_many_requests = True
            retry_after = int(r.headers["Retry-After"])
            time.sleep(max(60, retry_after))
            self.too_many_requests = False

            return self.run_query(query)
        elif r.status_code != 200:
            raise Exception(r.content)

        results: list[dict] = r.json()["results"]["bindings"]
        return results
