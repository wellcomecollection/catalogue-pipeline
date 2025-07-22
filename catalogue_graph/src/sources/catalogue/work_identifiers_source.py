from collections.abc import Generator

from pydantic import BaseModel
from utils.types import WorkIdentifiersKey

from sources.base_source import BaseSource
from sources.elasticsearch_source import ElasticsearchSource


class RawDenormalisedWorkIdentifier(BaseModel):
    identifier: dict
    referenced_in: WorkIdentifiersKey
    work_id: str
    work_collection_path: str | None


def extract_identifiers_from_work(
    raw_work: dict,
) -> Generator[RawDenormalisedWorkIdentifier]:
    work_id = raw_work["state"]["canonicalId"]
    work_data = raw_work.get("data", {})
    collection_path = work_data.get("collectionPath", {}).get("path")

    yield RawDenormalisedWorkIdentifier(
        identifier=raw_work["state"]["sourceIdentifier"],
        referenced_in="sourceIdentifier",
        work_id=work_id,
        work_collection_path=collection_path,
    )

    for identifier in work_data.get("otherIdentifiers", []):
        yield RawDenormalisedWorkIdentifier(
            identifier=identifier,
            referenced_in="otherIdentifiers",
            work_id=work_id,
            work_collection_path=collection_path,
        )


class CatalogueWorkIdentifiersSource(BaseSource):
    def __init__(
        self,
        pipeline_date: str | None,
        is_local: bool,
        query: dict | None = None,
        fields: list | None = None,
    ):
        self.es_source = ElasticsearchSource(pipeline_date, is_local, query, fields)

    def stream_raw(self) -> Generator[RawDenormalisedWorkIdentifier]:
        for work in self.es_source.stream_raw():
            yield from extract_identifiers_from_work(work)
