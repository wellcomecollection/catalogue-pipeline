from adapters.ebsco.transformers.genres import build_primary_concept
from adapters.ebsco.transformers.label_subdivisions import (
    build_label_with_subdivisions,
    build_subdivision_concepts,
)
from pymarc.record import Field, Subfield


def _field(tag: str, subs: list[tuple[str, str]]) -> Field:
    return Field(tag=tag, subfields=[Subfield(code=c, value=v) for c, v in subs])


def test_label_join_uses_hyphen_separator() -> None:
    field = _field(
        "655",
        [
            ("a", "Disco Polo"),
            ("v", "Specimens"),
            ("x", "Literature"),
            ("y", "1897-1900"),
            ("z", "Dublin."),
        ],
    )
    label = build_label_with_subdivisions(field)
    assert label == "Disco Polo - Specimens - Literature - 1897-1900 - Dublin"


def test_concept_types_for_subdivisions() -> None:
    field = _field(
        "655",
        [("a", "Music"), ("y", "1990-2000"), ("z", "London."), ("v", "Scores")],
    )
    primary_concept = build_primary_concept(field)
    concepts = (
        [primary_concept] if primary_concept else []
    ) + build_subdivision_concepts(field)
    labels = [c.label for c in concepts]
    types = [c.type for c in concepts]

    # Place retains trailing period (colon-only trimming for Place)
    assert labels == ["Music", "1990-2000", "London.", "Scores"]
    assert types == ["GenreConcept", "Period", "Place", "Concept"]
