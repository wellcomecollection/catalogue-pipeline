from collections.abc import Generator

from sources.base_source import BaseSource
from sources.gzip_source import GZipSource

CONCEPT_KEYS = ["subjects", "genres", "contributors"]

class CatalogueConceptsSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[dict]:
        """Streams raw concept nodes from a work's subjects, genres, and contributors."""
        catalogue_source = GZipSource(self.url)
        for work in catalogue_source.stream_raw():
            for conecpt_key in CONCEPT_KEYS:
                for raw_concept in work.get(conecpt_key, []):
                    yield raw_concept
