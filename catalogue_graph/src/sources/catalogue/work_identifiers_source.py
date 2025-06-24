from collections.abc import Generator

from utils.types import WorkIdentifiersKey

from sources.elasticsearch_source import ElasticsearchSource


def extract_identifiers_from_work(
    raw_work: dict,
) -> Generator[tuple[dict, str, WorkIdentifiersKey]]:
    work_data = raw_work.get("data", {})
    collection_path = work_data.get("collectionPath", {}).get("path")
    
    yield raw_work["state"]["sourceIdentifier"], collection_path, "sourceIdentifier"
    
    for identifier in work_data.get("otherIdentifiers", []):
        yield identifier, collection_path, "otherIdentifiers"


class CatalogueWorkIdentifiersSource(ElasticsearchSource):
    def __init__(self, url: str, index_name: str, basic_auth: tuple[str, str]):
        super().__init__(url, index_name, basic_auth)

    def stream_raw(self) -> Generator[tuple[dict, str, WorkIdentifiersKey]]:
        for work in super().stream_raw():
            yield from extract_identifiers_from_work(work)
