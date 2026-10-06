import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.marc.genres import (
    build_subdivision_concepts,
    extract_genre,
    extract_genres,
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
        ("Monica. N.O.R.A.D. A.B.C. BBQ", "monica norad abc bbq"),
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


def test_genres_with_the_same_label_but_different_identifiers_are_both_kept() -> None:
    record = Record(
        fields=[
            Field(
                tag="655",
                indicators=Indicators(" ", "0"),
                subfields=[
                    Subfield(code="a", value="Electronic journals"),
                    Subfield(code="0", value="sh92000896"),
                ],
            ),
            _field("655", [("a", "Electronic journals")]),
        ]
    )

    genres = extract_genres(record)

    assert [genre.label for genre in genres] == ["Electronic journals"] * 2
    ids = [genre.concepts[0].id for genre in genres]
    assert [i.source_identifier.value for i in ids if isinstance(i, Identifiable)] == [
        "sh92000896",
        "electronic journals",
    ]
