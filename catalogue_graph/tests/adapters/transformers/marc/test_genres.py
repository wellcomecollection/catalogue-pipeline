import pytest
from pymarc.record import Field, Subfield

from adapters.transformers.marc.genres import (
    build_subdivision_concepts,
    extract_genre,
)
from models.pipeline.identifier import Identifiable


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
    genre = extract_genre(field)
    assert genre is not None
    assert genre.label == "Disco Polo - Specimens - Literature - 1897-1900 - Dublin"


def test_concept_types_for_subdivisions() -> None:
    field = _field(
        "655",
        [("a", "Music"), ("y", "1990-2000"), ("z", "London."), ("v", "Scores")],
    )
    genre = extract_genre(field)
    assert genre is not None
    concepts = genre.concepts
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
        ("Gaul, ca. 50 B.C.", "gaul, ca 50 bc"),
        ("Monica. N.O.R.A.D. A.B.C. BBQ", "monica. n.o.r.a.d. a.b.c. bbq"),
    ],
)
def test_period_subdivision_identifiers(y_value: str, period_id: str) -> None:
    field = _field(
        "655",
        [("a", "Disco Polo"), ("y", y_value)],
    )
    concepts = build_subdivision_concepts(field)
    identifier = concepts[0].id
    assert isinstance(identifier, Identifiable)
    assert identifier.source_identifier.value == period_id
