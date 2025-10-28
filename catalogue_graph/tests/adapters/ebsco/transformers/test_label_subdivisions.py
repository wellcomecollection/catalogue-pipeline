from pymarc.record import Field, Subfield

from adapters.ebsco.transformers.label_subdivisions import (
    build_label_with_subdivisions,
    primary_and_subdivision_concepts,
)


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
    concepts = primary_and_subdivision_concepts(field, primary_type="Genre")
    labels = [c.label for c in concepts]
    types = [c.type for c in concepts]
    assert labels == ["Music", "1990-2000", "London", "Scores"]
    assert types == ["Genre", "Period", "Place", "Concept"]
