from collections.abc import Generator

from sources.base_source import BaseSource
from sources.gzip_source import GZipSource
from utils.types import WorkConceptKey

CONCEPT_KEYS: list[WorkConceptKey] = ["subjects", "genres", "contributors"]


class CatalogueConceptsSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[dict]:
        """Streams raw concept nodes from a work's subjects, genres, and contributors."""
        catalogue_source = GZipSource(self.url)
        for work in catalogue_source.stream_raw():
            for concept_key in CONCEPT_KEYS:
                yield from work.get(concept_key, [])
