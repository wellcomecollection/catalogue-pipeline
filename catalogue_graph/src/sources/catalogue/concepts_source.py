from collections.abc import Generator

from sources.base_source import BaseSource
from sources.gzip_source import GZipSource
from utils.types import WorkConceptKey


def extract_concepts_from_work(
    raw_work: dict,
) -> Generator[tuple[dict, WorkConceptKey]]:
    """Returns all concepts associated with the given work. Does not deduplicate."""
    # We need to return all concepts stored in each subject, and also the subject itself.
    # This will sometimes result in duplicates being returned.
    for subject in raw_work.get("subjects", []):
        for concept in subject.get("concepts", []):
            yield concept, "subjects"
        yield subject, "subjects"

    # Return all contributors
    for contributor in raw_work.get("contributors", []):
        yield contributor["agent"], "contributors"

    # Return all concepts stored as part of each genre
    for genre in raw_work.get("genres", []):
        for concept in genre.get("concepts", []):
            yield concept, "genres"


class CatalogueConceptsSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[tuple[dict, WorkConceptKey]]:
        """Streams raw concept nodes from a work's subjects, genres, and contributors."""
        catalogue_source = GZipSource(self.url)
        for work in catalogue_source.stream_raw():
            yield from extract_concepts_from_work(work)
