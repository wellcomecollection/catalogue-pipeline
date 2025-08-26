from collections.abc import Generator

from sources.base_source import BaseSource
from sources.elasticsearch_source import ElasticsearchSource
from utils.types import WorkConceptKey


def extract_concepts_from_work(
    raw_work: dict,
) -> Generator[tuple[dict, WorkConceptKey]]:
    """Returns all concepts associated with the given work. Does not deduplicate."""
    # Some subjects contain nested component concepts. For example, the subject 'Milk - Quality' consists
    # of concepts 'Milk' and 'Quality' (each with its own Wellcome ID). For now, we are not interested in
    # extracting these component concepts, since the frontend does not make use of them and the resulting
    # theme pages would be empty.
    for subject in raw_work.get("subjects", []):
        # caveat concerning the above comment:
        # for non-composite concepts, identified as having the same id as the top-level subject,
        # the nested "Type" is more specific than the top-level "Type"
        # in that case, we copy the nested concept's "Type" onto the subject
        # Check if the first concept in the concepts list has the same id as the subject
        concepts = subject.get("concepts", [])
        if concepts and len(concepts) == 1 and concepts[0].get("id") == subject.get("id"):
            # Replace the subject's type with the concept's type
            subject_copy = subject.copy()
            subject_copy["type"] = concepts[0].get("type", subject.get("type"))
            yield subject_copy, "subjects"
        else:
            yield subject, "subjects"

    # Return all contributors
    for contributor in raw_work.get("contributors", []):
        yield contributor["agent"], "contributors"

    for genre in raw_work.get("genres", []):
        for concept in genre.get("concepts", []):
            yield concept, "genres"
            # Only extract the first item from each genre. Subsequent items are not associated with the work in
            # catalogue API filters and the resulting theme pages would be empty.
            break


class CatalogueConceptsSource(BaseSource):
    def __init__(
        self,
        pipeline_date: str | None,
        is_local: bool,
        query: dict | None = None,
        fields: list | None = None,
    ):
        self.es_source = ElasticsearchSource(pipeline_date, is_local, query, fields)

    def stream_raw(self) -> Generator[tuple[dict, WorkConceptKey]]:
        """Streams raw concept nodes from a work's subjects, genres, and contributors."""
        for work in self.es_source.stream_raw():
            yield from extract_concepts_from_work(work["data"])
