import pytest
from pymarc.record import Field, Subfield

from adapters.ebsco.transformers.genres import build_primary_concept
from adapters.ebsco.transformers.label_subdivisions import (
    build_label_with_subdivisions,
    build_subdivision_concepts,
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
    primary_concept = build_primary_concept(field)
    concepts = (
        [primary_concept] if primary_concept else []
    ) + build_subdivision_concepts(field)
    labels = [c.label for c in concepts]
    types = [c.type for c in concepts]

    assert labels == ["Music", "1990-2000", "London", "Scores"]
    assert types == ["GenreConcept", "Period", "Place", "Concept"]


@pytest.mark.parametrize(
    "y_value, period_id",
    [
        ("2000 A.D.", "2000 ad"),
        ("50 B.C.", "50 bc"),
        ("ca. 50 B.C.", "ca 50 bc"),
        ("Gaul, ca. 50 B.C.", "Gaul, ca 50 bc"),
        ("Monica. N.O.R.A.D. A.B.C.", "Monica. N.O.R.A.D. A.B.C."),
    ],
)
def test_period_subdivision_identifiers(y_value: str, period_id: str) -> None:
    field = _field(
        "655",
        [("a", "Disco Polo"), ("y", y_value)],
    )
    concepts = build_subdivision_concepts(field)
    assert concepts[1].id.source_identifier.value == period_id
