"""Tests covering extraction of parent/series relations from MARC.

Although the implementation currently lives under `adapters.ebsco.transformers.parents`,
these tests are MARC-field level and can be shared across adapters.
"""

# mypy: allow-untyped-calls

from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.parents import get_parents
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkAncestor, WorkRelations
from tests.adapters.marc.marcxml_test_transformer import MarcXmlTransformerForTests

test_cases = [
    (
        [
            Field(
                tag="440",
                subfields=[Subfield(code="a", value="A title from 440ǂa")],
            )
        ],
        ["A title from 440ǂa"],
    ),
    (
        [
            Field(
                tag="490",
                subfields=[Subfield(code="a", value="A title from 490ǂa")],
            )
        ],
        ["A title from 490ǂa"],
    ),
    (
        [
            Field(
                tag="773",
                subfields=[Subfield(code="t", value="A title from 773ǂt")],
            )
        ],
        ["A title from 773ǂt"],
    ),
    (
        [
            Field(
                tag="773",
                subfields=[Subfield(code="a", value="A title from 773ǂa")],
            )
        ],
        ["A title from 773ǂa"],
    ),
    (
        [
            Field(
                tag="773",
                subfields=[Subfield(code="s", value="A title from 773ǂs")],
            )
        ],
        ["A title from 773ǂs"],
    ),
    (
        [
            Field(
                tag="830",
                subfields=[Subfield(code="t", value="A title from 830ǂt")],
            )
        ],
        ["A title from 830ǂt"],
    ),
    (
        [
            Field(
                tag="830",
                subfields=[Subfield(code="a", value="A title from 830ǂa")],
            )
        ],
        ["A title from 830ǂa"],
    ),
    (
        [
            Field(
                tag="830",
                subfields=[
                    Subfield(code="t", value="A title from 830ǂt"),
                    Subfield(code="a", value="A title from 830ǂa"),
                ],
            )
        ],
        ["A title from 830ǂt"],
    ),
    (
        [
            Field(
                tag="830",
                subfields=[
                    Subfield(code="a", value="A title from 830ǂa"),
                    Subfield(code="s", value="A title from 830ǂs"),
                ],
            )
        ],
        ["A title from 830ǂa"],
    ),
    (
        [
            Field(
                tag="440",
                subfields=[Subfield(code="a", value="A title from 440ǂa")],
            ),
            Field(
                tag="490",
                subfields=[Subfield(code="a", value="A title from 490ǂa")],
            ),
        ],
        ["A title from 440ǂa", "A title from 490ǂa"],
    ),
    (
        [Field(tag="830", subfields=[Subfield(code="x", value="9999-9999")])],
        [],
    ),
]


@pytest.mark.parametrize("fields,expected", test_cases)
def test_get_parents(fields: list[Field], expected: list[str]) -> None:
    parents = get_parents(Record(fields=fields))
    assert [p.title for p in parents] == expected


def test_remove_suffix() -> None:
    fields = [
        Field(
            tag="440",
            subfields=[Subfield(code="a", value="Some title;")],
        )
    ]

    parents = get_parents(Record(fields=fields))
    assert [p.title for p in parents] == ["Some title"]


def test_remove_duplicates() -> None:
    fields = [
        Field(
            tag="440",
            subfields=[Subfield(code="a", value="   Some duplicate title   ;")],
        ),
        Field(
            tag="830",
            subfields=[Subfield(code="a", value="Some duplicate title,")],
        ),
    ]

    parents = get_parents(Record(fields=fields))
    assert [p.title for p in parents] == ["Some duplicate title"]


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="440",
                    subfields=[Subfield(code="a", value="A title from 440ǂa")],
                ),
            ]
        )
    ],
    indirect=["marc_record"],
)
def test_relations_are_set_from_parents(marc_record: Record) -> None:
    transformer = MarcXmlTransformerForTests(
        build_work_data=lambda _: WorkData(),
        build_relations=lambda r: WorkRelations(ancestors=get_parents(r)),
    )

    work = transformer.transform_marc_record(
        marc_record, source_modified_time=datetime.now()
    )

    assert work.state.relations == WorkRelations(
        ancestors=[
            WorkAncestor(
                title="A title from 440ǂa",
                work_type="Series",
                depth=0,
                num_children=0,
                num_descendents=0,
            )
        ]
    )
