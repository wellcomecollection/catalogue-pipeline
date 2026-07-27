from collections.abc import Generator, Iterable
from typing import Any

from ingestor.extractors.concepts.base_concepts_extractor import (
    GraphBaseConceptsExtractor,
    _choose_target_id,
)
from ingestor.models.neptune.query_result import ExtractedConcept
from tests.mocks import get_mock_neptune_client

SOURCE_CONCEPT_ID = "aaaaaaaa"

# 'th3tx5an' sorts first, so it is the primary.
UNCONNECTED_PRIMARY_ID = "th3tx5an"
WORK_CONNECTED_ID = "wsg7zfsq"


def _an_extracted_concept(concept_id: str) -> ExtractedConcept:
    return ExtractedConcept.model_validate(
        {
            "concept": {
                "~id": concept_id,
                "~entityType": "node",
                "~labels": ["Concept"],
                "~properties": {
                    "id": concept_id,
                    "label": "Labor supply",
                    "source": "lc-subjects",
                    "type": "Concept",
                },
            },
            "source_concepts": [],
            "linked_source_concepts": [],
            "types": ["Concept"],
        }
    )


class StubConceptsExtractor(GraphBaseConceptsExtractor):
    """Concepts extractor with canned Neptune responses. IDs in `related` all have works, as the queries filter on HAS_CONCEPT."""

    def __init__(
        self, related: dict[str, list[str]], same_as_groups: dict[str, list[str]]
    ) -> None:
        super().__init__(get_mock_neptune_client())
        self.related = related
        self.same_as_groups = same_as_groups

    def get_concept_ids_to_process(self) -> Generator[str]:
        yield from self.related

    def extract_raw(self) -> Generator[Any]:
        yield from ()

    def make_neptune_query(
        self, query_type: Any, ids: Iterable[str]
    ) -> dict[str, dict]:
        if query_type == "same_as_concept":
            return {
                i: {"same_as_ids": self.same_as_groups.get(i, [])}
                for i in ids
                if i in self.same_as_groups
            }

        return {
            i: {
                "related": [
                    {"id": related_id, "count": 1, "relationship_type": None}
                    for related_id in self.related[i]
                ]
            }
            for i in ids
            if i in self.related
        }

    def get_concepts(self, ids: Iterable[str]) -> dict[str, ExtractedConcept]:
        return {i: _an_extracted_concept(i) for i in ids}


def test_choose_target_id_prefers_the_primary_when_it_is_referenced() -> None:
    assert _choose_target_id("abcdefgh", {"abcdefgh", "zzzzzzzz"}) == "abcdefgh"


def test_choose_target_id_falls_back_when_the_primary_is_not_referenced() -> None:
    assert _choose_target_id("abcdefgh", {"wwwwwwww", "zzzzzzzz"}) == "wwwwwwww"


def test_related_concept_target_skips_a_primary_with_no_works() -> None:
    """See platform#6388: referring to a work-less concept produced a link which 404d."""
    extractor = StubConceptsExtractor(
        related={SOURCE_CONCEPT_ID: [WORK_CONNECTED_ID]},
        same_as_groups={WORK_CONNECTED_ID: [UNCONNECTED_PRIMARY_ID]},
    )

    result = extractor._get_related_concepts("broader_than", [SOURCE_CONCEPT_ID])

    targets = [r.target.concept.properties.id for r in result[SOURCE_CONCEPT_ID]]
    assert targets == [WORK_CONNECTED_ID]


def test_related_concepts_still_merge_onto_a_referenced_primary() -> None:
    """Synonymous related concepts still merge under the primary when it has works itself."""
    extractor = StubConceptsExtractor(
        related={SOURCE_CONCEPT_ID: [WORK_CONNECTED_ID, UNCONNECTED_PRIMARY_ID]},
        same_as_groups={
            WORK_CONNECTED_ID: [UNCONNECTED_PRIMARY_ID],
            UNCONNECTED_PRIMARY_ID: [WORK_CONNECTED_ID],
        },
    )

    result = extractor._get_related_concepts("broader_than", [SOURCE_CONCEPT_ID])

    targets = [r.target.concept.properties.id for r in result[SOURCE_CONCEPT_ID]]
    assert targets == [UNCONNECTED_PRIMARY_ID]
