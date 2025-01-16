import requests


class WikidataSparqlClient:
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
        r = requests.get(
            "https://query.wikidata.org/sparql",
            params={"format": "json", "query": query},
            headers={"User-Agent": self._get_user_agent_header()},
        )

        if r.status_code != 200:
            raise Exception(r.content)

        results: list[dict] = r.json()["results"]["bindings"]
        return results
