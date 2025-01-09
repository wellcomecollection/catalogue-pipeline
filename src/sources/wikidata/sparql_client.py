import requests


class WikidataSparqlClient:
    @staticmethod
    def _get_user_agent_header():
        # https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
        return (
            "WellcomeCollectionCatalogueGraphPipeline/0.1 (https://wellcomecollection.org/; "
            "digital@wellcomecollection.org) wellcome-collection-catalogue-graph/0.1"
        )

    def run_query(self, query: str) -> list[dict]:
        r = requests.get(
            "https://query.wikidata.org/sparql",
            params={"format": "json", "query": query},
            headers={"User-Agent": self._get_user_agent_header()},
        )

        if r.status_code != 200:
            raise Exception(r.content)

        return r.json()["results"]["bindings"]
