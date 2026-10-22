"""
Give subjects and contributors that share an identifier the same type.

A heading's type comes from its MARC tag, so one authority can arrive as a Place
in a 651 and an Organisation in a 710, or as a Person in a 100 and an Agent in a
700 name/title entry in a single work. The type belongs to the identifier:
if the work names the same identifier under a more specific type anywhere,
that is the type. The catalogue graph resolves each concept's type across all
works with a fixed priority and applying that priority within a work makes its
subjects and contributors more consistent with the concept they link to.
"""

import copy
from collections import defaultdict
from typing import cast

from adapters.transformers.marc.common import distinct
from adapters.transformers.marc.contributors import distinct_contributors
from models.pipeline.concept import Concept, Contributor, Subject
from models.pipeline.identifier import Identifiable, SourceIdentifier
from models.pipeline.work_data import WorkData
from utils.concept_types import get_most_specific_concept_type
from utils.types import ConceptType, RawConceptType

IdentifierKey = tuple[str, str]


def harmonise_work_data(data: WorkData) -> WorkData:
    subjects, contributors = harmonise_types(data.subjects, data.contributors)
    return data.model_copy(update={"subjects": subjects, "contributors": contributors})


def harmonise_types(
    subjects: list[Subject], contributors: list[Contributor]
) -> tuple[list[Subject], list[Contributor]]:
    """Retype subjects and contributors that share an identifier to the most specific type among them."""
    types: dict[IdentifierKey, set[ConceptType]] = defaultdict(set)

    harmonised_concepts = subjects + [c.agent for c in contributors]
    for concept in harmonised_concepts:
        if identifier := _identifier(concept):
            types[_key(identifier)].add(cast(ConceptType, identifier.ontology_type))

    most_specific_types = {
        key: get_most_specific_concept_type(list(concept_types))
        for key, concept_types in types.items()
        if len(concept_types) > 1
    }
    if not most_specific_types:
        return subjects, contributors

    # Make a deep copy and retype each entry in place.
    subjects, contributors = copy.deepcopy((subjects, contributors))
    for subject in subjects:
        _retype_subject(subject, most_specific_types)
    for contributor in contributors:
        _retype_contributor(contributor, most_specific_types)
    return distinct(subjects), distinct_contributors(contributors)


def _identifier(concept: Concept) -> SourceIdentifier | None:
    return (
        concept.id.source_identifier if isinstance(concept.id, Identifiable) else None
    )


def _key(identifier: SourceIdentifier) -> IdentifierKey:
    return identifier.identifier_type.id, identifier.value


def _retype_concept(concept: Concept, concept_type: ConceptType) -> None:
    assert isinstance(concept.id, Identifiable)
    # Genres are not harmonised, so the one type the two aliases
    # disagree on (Genre vs Concept) never arrives here
    concept.type = cast(RawConceptType, concept_type)
    concept.id.source_identifier.ontology_type = concept_type


def _retype_subject(
    subject: Subject, most_specific_types: dict[IdentifierKey, ConceptType]
) -> None:
    identifier = _identifier(subject)
    if identifier is None:
        return
    concept_type = most_specific_types.get(_key(identifier))
    if concept_type is None:
        return
    identifier.ontology_type = concept_type
    # Only the concept carrying the heading's own identifier changes type;
    # subdivision concepts have identifiers of their own
    for concept in subject.concepts:
        concept_identifier = _identifier(concept)
        if concept_identifier and _key(concept_identifier) == _key(identifier):
            _retype_concept(concept, concept_type)


def _retype_contributor(
    contributor: Contributor, most_specific_types: dict[IdentifierKey, ConceptType]
) -> None:
    identifier = _identifier(contributor.agent)
    if identifier is None:
        return
    concept_type = most_specific_types.get(_key(identifier))
    if concept_type is not None:
        _retype_concept(contributor.agent, concept_type)
