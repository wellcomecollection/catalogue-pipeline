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
        relationship_types: dict[str, str] | None = None,
    ) -> None:
        super().__init__(get_mock_neptune_client())
        self.related = related
        self.same_as_groups = same_as_groups
        self.work_connected = work_connected
        self.relationship_types = relationship_types or {}

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
                    {
                        "id": related_id,
                        "count": 1,
                        "relationship_type": self.relationship_types.get(related_id),
                    }
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


def test_related_concept_relationship_type_is_chosen_deterministically() -> None:
    extractor = StubConceptsExtractor(
        related={SOURCE_CONCEPT_ID: [SIBLING_ID, PRIMARY_ID]},
        same_as_groups={PRIMARY_ID: [SIBLING_ID], SIBLING_ID: [PRIMARY_ID]},
        work_connected={PRIMARY_ID, SIBLING_ID},
        relationship_types={PRIMARY_ID: "has_sibling", SIBLING_ID: "has_parent"},
    )
    result = extractor._get_related_concepts("related_to", [SOURCE_CONCEPT_ID])

    assert [r.relationship_type for r in result[SOURCE_CONCEPT_ID]] == ["has_parent"]


def test_same_as_map_does_not_depend_on_row_order() -> None:
    other_id = "zzzzzzzz"
    extractor = StubConceptsExtractor(
        related={},
        same_as_groups={
            SIBLING_ID: [other_id, PRIMARY_ID],
            other_id: [SIBLING_ID, PRIMARY_ID],
            PRIMARY_ID: [other_id, SIBLING_ID],
        },
        work_connected=set(),
    )
    extractor._update_same_as_map([other_id, SIBLING_ID, PRIMARY_ID])

    expected = [PRIMARY_ID, SIBLING_ID, other_id]
    assert [extractor.get_same_as(i) for i in expected] == [expected] * 3


def _a_source_concept(source_concept_id: str) -> dict:
    return {
        "~id": source_concept_id,
        "~labels": ["SourceConcept"],
        "~properties": {"id": source_concept_id, "source": "nlm-mesh"},
    }


def test_resolved_source_concepts_are_sorted() -> None:
    extractor = StubConceptsExtractor(
        related={},
        same_as_groups={SIBLING_ID: [PRIMARY_ID], PRIMARY_ID: [SIBLING_ID]},
        work_connected=set(),
    )
    extractor._update_same_as_map([SIBLING_ID])
    source_concepts_batch = {
        PRIMARY_ID: {"source_concepts": [_a_source_concept("D019493")]},
        SIBLING_ID: {
            "source_concepts": [
                _a_source_concept("D019494"),
                _a_source_concept("D019492"),
            ]
        },
    }

    resolved = extractor._resolve_source_concepts(SIBLING_ID, source_concepts_batch)

    assert [sc.id for sc in resolved] == ["D019492", "D019493", "D019494"]
