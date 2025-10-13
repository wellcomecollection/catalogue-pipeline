from collections.abc import Generator

from models.events import BasePipelineEvent
from sources.base_source import BaseSource
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode
from utils.types import WorkConceptKey


def extract_concepts_from_work(
    raw_merged_work: dict,
) -> Generator[tuple[dict, WorkConceptKey]]:
    """Returns all concepts associated with the given work. Does not deduplicate."""

    data = raw_merged_work.get("data", {})

    # Some subjects contain nested component concepts. For example, the subject 'Milk - Quality' consists
    # of concepts 'Milk' and 'Quality' (each with its own Wellcome ID). For now, we are not interested in
    # extracting these component concepts, since the frontend does not make use of them and the resulting
    # theme pages would be empty.
    # However, an exception exists for simple, non-composite subjects where the nested concept
    # is the subject itself (identified by matching IDs). In this specific case, the nested
    # concept's "Type" is more specific, so we promote it to the top-level subject.
    for subject in data.get("subjects", []):
        new_type = "Subject"

        concepts = subject.get("concepts", [])
        if len(concepts) == 1 and concepts[0].get("id") == subject.get("id"):
            # If the case matches, use the concept's type, falling back to the subject's original type
            concept = concepts[0]
            new_type = concept.get("type", subject.get("type"))

        new_subject = subject.copy()
        new_subject["type"] = new_type
        yield new_subject, "subjects"

    # Return all contributors
    for contributor in data.get("contributors", []):
        yield contributor["agent"], "contributors"

    for genre in data.get("genres", []):
        for concept in genre.get("concepts", []):
            # All concepts extracted from the 'genres' section are always of type 'Genre'
            # (but the merged index uses the term 'GenreConcept').
            yield {**concept, "type": "Genre"}, "genres"
            # Only extract the first item from each genre. Subsequent items are not associated with the work in
            # catalogue API filters and the resulting theme pages would be empty.
            break


class CatalogueConceptsSource(BaseSource):
    def __init__(
        self,
        event: BasePipelineEvent,
        query: dict | None = None,
        fields: list | None = None,
        es_mode: ElasticsearchMode = "private",
    ):
        self.es_source = MergedWorksSource(
            event, query=query, fields=fields, es_mode=es_mode
        )

    def stream_raw(self) -> Generator[tuple[dict, WorkConceptKey]]:
        """Streams raw concept nodes from a work's subjects, genres, and contributors."""
        for work in self.es_source.stream_raw():
            yield from extract_concepts_from_work(work)
