from collections.abc import Generator, Iterable
from typing import Any

from ingestor.extractors.concepts.base_concepts_extractor import (
    GraphBaseConceptsExtractor,
    _choose_target_id,
)
from ingestor.models.neptune.query_result import ExtractedConcept
from tests.mocks import get_mock_neptune_client

SOURCE_CONCEPT_ID = "aaaaaaaa"

# 'th3tx5an' sorts first, so it is the primary of the group.
PRIMARY_ID = "th3tx5an"
SIBLING_ID = "wsg7zfsq"


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
    """Concepts extractor with canned Neptune responses.

    `related` maps a source concept ID to the related concept IDs the graph returned for it. The related queries
    collapse each 'same as' group to one arbitrary member, so that is not the full set of members with works.
    `work_connected` is the set of IDs which have works.
    """

    def __init__(
        self,
        related: dict[str, list[str]],
        same_as_groups: dict[str, list[str]],
        work_connected: set[str],
    ) -> None:
        super().__init__(get_mock_neptune_client())
        self.related = related
        self.same_as_groups = same_as_groups
        self.work_connected = work_connected

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

        if query_type == "concept_type":
            return {i: {"types": ["Concept"]} for i in ids if i in self.work_connected}

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


def _related_targets(related_ids: list[str], work_connected: set[str]) -> list[str]:
    extractor = StubConceptsExtractor(
        related={SOURCE_CONCEPT_ID: related_ids},
        same_as_groups={PRIMARY_ID: [SIBLING_ID], SIBLING_ID: [PRIMARY_ID]},
        work_connected=work_connected,
    )
    result = extractor._get_related_concepts("broader_than", [SOURCE_CONCEPT_ID])
    return [r.target.concept.properties.id for r in result[SOURCE_CONCEPT_ID]]


def test_choose_target_id_prefers_the_primary() -> None:
    assert _choose_target_id("abcdefgh", {"abcdefgh", "zzzzzzzz"}) == "abcdefgh"


def test_choose_target_id_falls_back_to_the_first_candidate() -> None:
    assert _choose_target_id("abcdefgh", {"wwwwwwww", "zzzzzzzz"}) == "wwwwwwww"


def test_related_concept_target_skips_a_primary_with_no_works() -> None:
    """See platform#6388: referring to a work-less concept produced a link which 404d."""
    assert _related_targets([SIBLING_ID], work_connected={SIBLING_ID}) == [SIBLING_ID]


def test_related_concept_target_keeps_a_primary_which_has_works() -> None:
    """The related query returns one arbitrary group member, which must not displace a valid primary."""
    assert _related_targets([SIBLING_ID], work_connected={PRIMARY_ID, SIBLING_ID}) == [
        PRIMARY_ID
    ]


def test_related_concepts_merge_onto_one_target() -> None:
    """Synonymous related concepts merge under a single entry rather than appearing twice."""
    assert _related_targets(
        [SIBLING_ID, PRIMARY_ID], work_connected={PRIMARY_ID, SIBLING_ID}
    ) == [PRIMARY_ID]
