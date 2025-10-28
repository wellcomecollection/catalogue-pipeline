import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.parents import get_parents

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
