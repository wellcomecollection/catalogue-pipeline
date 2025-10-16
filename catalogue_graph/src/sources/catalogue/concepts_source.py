from collections.abc import Generator

from pydantic import BaseModel

from models.events import BasePipelineEvent
from models.pipeline.concept import (
    Concept,
    Contributor,
    Genre,
    IdentifiedConcept,
    Subject,
)
from sources.base_source import BaseSource
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode
from utils.types import WorkConceptKey


class MergedWorkConceptsData(BaseModel):
    subjects: list[Subject] = []
    genres: list[Genre] = []
    contributors: list[Contributor] = []

    def extract_concepts(self) -> Generator[tuple[Concept, WorkConceptKey]]:
        """Return all concepts associated with the work."""

        # Some subjects contain nested component concepts. For example, the subject 'Milk - Quality' consists
        # of concepts 'Milk' and 'Quality' (each with its own Wellcome ID). For now, we are not interested in
        # extracting these component concepts, since the frontend does not make use of them and the resulting
        # theme pages would be empty.
        # However, an exception exists for simple, non-composite subjects where the nested concept
        # is the subject itself (identified by matching IDs). In this specific case, the nested
        # concept's "Type" is more specific, so we promote it to the top-level subject.
        for subject in self.subjects:
            new_type = "Subject"
            if len(subject.concepts) == 1 and subject.concepts[0].id == subject.id:
                # If the case matches, use the concept's type
                new_type = subject.concepts[0].type

            yield subject.model_copy(update={"type": new_type}), "subjects"

        # Extract all contributors
        for contributor in self.contributors:
            yield contributor.agent, "contributors"

        for genre in self.genres:
            if genre.concepts:
                # Only extract the first item from each genre. Subsequent items are not associated with the work in
                # catalogue API filters and the resulting theme pages would be empty.
                yield genre.concepts[0], "genres"

    def extract_identified_concepts(
        self,
    ) -> Generator[tuple[IdentifiedConcept, WorkConceptKey]]:
        for concept, referenced_in in self.extract_concepts():
            if concept.id.canonical_id is not None:
                yield IdentifiedConcept.from_concept(concept), referenced_in


class ExtractedWorkConcept(BaseModel):
    concept: IdentifiedConcept
    referenced_in: WorkConceptKey
    work_id: str


ES_QUERY = {"match": {"type": "Visible"}}
ES_FIELDS = ["state.canonicalId", "data.subjects", "data.contributors", "data.genres"]


class CatalogueConceptsSource(BaseSource):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_mode: ElasticsearchMode = "private",
    ):
        self.es_source = MergedWorksSource(
            event, query=ES_QUERY, fields=ES_FIELDS, es_mode=es_mode
        )

    def stream_raw(self) -> Generator[ExtractedWorkConcept]:
        """Streams raw concept nodes from a work's subjects, genres, and contributors."""
        for work in self.es_source.stream_raw():
            work_data = MergedWorkConceptsData.model_validate(work["data"])

            for concept, referenced_in in work_data.extract_identified_concepts():
                yield ExtractedWorkConcept(
                    work_id=work["state"]["canonicalId"],
                    concept=concept,
                    referenced_in=referenced_in,
                )
