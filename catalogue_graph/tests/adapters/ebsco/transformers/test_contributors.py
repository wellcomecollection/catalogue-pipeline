import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.common import normalise_identifier_value
from adapters.ebsco.transformers.ebsco_to_weco import transform_record
from models.pipeline.concept import Concept
from models.pipeline.id_label import Label
from models.pipeline.identifier import Id, Identifiable, SourceIdentifier
from utils.types import ConceptType

from ..helpers import lone_element


def test_no_contributors(marc_record: Record) -> None:
    assert transform_record(marc_record).data.contributors == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="100",
                    subfields=[Subfield(code="a", value="Jane Example")],
                )
            ],
            id="single-contributor",
        )
    ],
    indirect=["marc_record"],
)
def test_contributor_id_default_unidentifiable(marc_record: Record) -> None:
    """Ensure each contributor object itself has an Unidentifiable id by default.

    The agent (Concept) receives an identifier derived from the label, but the
    top-level Contributor model should retain its default Unidentifiable id
    unless explicitly set elsewhere.
    """
    from models.pipeline.identifier import Unidentifiable

    contributor = transform_record(marc_record).data.contributors[0]
    assert isinstance(contributor.id, Unidentifiable)


@pytest.mark.parametrize(
    "marc_record,field_code",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    subfields=[Subfield(code="a", value="J. R. Hartley")],
                )
            ],
            code,
            id=f"MARC field code: {code}",
        )
        for code in ["100", "110", "111", "700", "710", "711"]
    ],
    indirect=["marc_record"],
)
def test_contributor_from_field(marc_record: Record, field_code: str) -> None:
    assert (
        transform_record(marc_record).data.contributors[0].agent.label
        == "J. R. Hartley"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=f"7{code_suffix}",
                    subfields=[
                        Subfield(code="a", value="James Moriarty"),
                        Subfield(
                            code="t", value="A Treatise on the Binomial Theorem"
                        ),  # name of work, not the person
                    ],
                ),
                Field(
                    tag=f"1{code_suffix}",
                    subfields=[Subfield(code="a", value="James Moriarty")],
                ),
            ],
            id=concept_type,
        )
        for (code_suffix, concept_type) in [
            ("00", "Person"),
            ("10", "Organisation"),
            ("11", "Meeting"),
        ]
    ],
    indirect=["marc_record"],
)
def test_distinct_by_label(marc_record: Record) -> None:
    work = transform_record(marc_record)
    contributor = lone_element(work.data.contributors)
    #    assert len(work.contributors) == 1
    assert contributor.agent.label == "James Moriarty"
    # if one is primary and the other not, then the primary one is retained
    # A real example of this can be seen in y2xdytd7 (ebs1351010e)
    # where the primary contributor in the EBSCO data is the author,
    # and the secondary, his work.
    assert contributor.primary


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=f"7{code_suffix}",
                    subfields=[
                        Subfield(code="a", value="James Moriarty"),
                        Subfield(
                            code="t", value="A Treatise on the Binomial Theorem"
                        ),  # name of work, not the person
                    ],
                ),
                Field(
                    tag=f"7{code_suffix}",
                    subfields=[Subfield(code="a", value="James Moriarty")],
                ),
            ],
            id=concept_type,
        )
        for (code_suffix, concept_type) in [
            ("00", "Person"),
            ("10", "Organisation"),
            ("11", "Meeting"),
        ]
    ],
    indirect=["marc_record"],
)
def test_distinct_by_label_no_primary(marc_record: Record) -> None:
    work = transform_record(marc_record)
    assert len(work.data.contributors) == 1
    assert work.data.contributors[0].agent.label == "James Moriarty"
    # there are no examples of this in real data, where neither
    # matching subfield is a primary (Main Entry) type,
    # but we shouldn't be making up primary contributors when there are none
    assert not work.data.contributors[0].primary


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="100",
                    subfields=[
                        Subfield(code="a", value="Dora Milaje"),
                        Subfield(
                            code="t", value="The Princess Bride"
                        ),  # name of work, not the person
                    ],
                ),
                Field(
                    tag="710",
                    subfields=[Subfield(code="a", value="Dora Milaje")],
                ),
            ],
            id="Person/Organisation",
        )
    ],
    indirect=["marc_record"],
)
def test_distinct_by_label_and_type(marc_record: Record) -> None:
    """
    If we have two contributors who share the same name, but
    are of different types, then they are different.
    :param marc_record:
    :return:
    """
    work = transform_record(marc_record)
    assert len(work.data.contributors) == 2
    assert work.data.contributors[0].agent.label == "Dora Milaje"
    assert work.data.contributors[1].agent.label == "Dora Milaje"
    assert work.data.contributors[0].agent.type == "Person"
    assert work.data.contributors[1].agent.type == "Organisation"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=f"7{code_suffix}",
                    subfields=[
                        Subfield(code="a", value="James Moriarty"),
                        Subfield(code="e", value="Author"),
                    ],
                ),
                Field(
                    tag=f"1{code_suffix}",
                    subfields=[
                        Subfield(code="a", value="James Moriarty"),
                        Subfield(code="e", value="Mastermind"),
                    ],
                ),
            ],
            id=concept_type,
        )
        for (code_suffix, concept_type) in [
            ("00", "Person"),
            ("10", "Organisation"),
            # Watch this.  The correct role subfield for meeting is "j", but EBSCO data includes it in "e"
            ("11", "Meeting"),
        ]
    ],
    indirect=["marc_record"],
)
def test_distinct_by_label_and_role(marc_record: Record) -> None:
    work = transform_record(marc_record)
    assert len(work.data.contributors) == 2
    assert work.data.contributors[1].roles == [Label(label="Author")]
    assert work.data.contributors[0].roles == [Label(label="Mastermind")]


@pytest.mark.parametrize(
    "marc_record,field_code,ontology_type,primary",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    subfields=[
                        Subfield(code="a", value="Churchill, Randolph Spencer"),
                        Subfield(code="b", value="IV,"),
                        Subfield(code="c", value="Lady,"),
                        Subfield(code="d", value="1856-1939"),
                        Subfield(
                            code="t", value="IGNORE ME!"
                        ),  # name of work, not the person
                        Subfield(
                            code="n", value="IGNORE ME!"
                        ),  # regards section of work mentioned in t
                        Subfield(
                            code="p", value="IGNORE ME!"
                        ),  # regards section of work mentioned in t
                        Subfield(code="q", value="(nee Jennie Jerome)"),
                        Subfield(code="l", value="Ayapeneco"),  # language of "t"
                        Subfield(code="e", value="key grip"),  # language of "t"
                        Subfield(code="e", value="best boy"),  # language of "t"
                    ],
                )
            ],
            code,
            ontology_type,
            primary,
            id=f"{code}: {ontology_type}",
        )
        for (code, ontology_type, primary) in [
            ("100", "Person", True),
            ("700", "Person", False),
            ("110", "Organisation", True),
            ("710", "Organisation", False),
        ]
    ],
    indirect=["marc_record"],
)
def test_contributor_all_fields(
    marc_record: Record, field_code: str, ontology_type: ConceptType, primary: bool
) -> None:
    # A previous incarnation of this transformer included fields t,n,p and l in the label.
    # Collectively, those fields identified some work by the named person, as though citing
    # them in a bibliography. This is not what we currently use the contributor field for,
    # All we want is the actual person.
    # Similarly, the previous incarnation had differing subfiueld lists for Organisation and Person.
    # This is not necessary, as the only field that now differs between the two is $q,
    # which does not exist on x10 fields.
    contributor = transform_record(marc_record).data.contributors[0]
    assert contributor.roles == [Label(label="key grip"), Label(label="best boy")]
    label = "Churchill, Randolph Spencer IV, Lady, 1856-1939 (nee Jennie Jerome)"
    assert contributor.primary == primary
    assert contributor.agent.label == label

    assert contributor.agent == Concept(
        type=ontology_type,
        label=label,
        id=Identifiable(
            source_identifier=SourceIdentifier(
                identifier_type=Id(id="label-derived"),
                ontology_type=ontology_type,
                value=normalise_identifier_value(label),
            ),
            other_identifiers=[],
        ),
    )


@pytest.mark.parametrize(
    "marc_record,field_code,primary",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    subfields=[
                        Subfield(code="a", value="Council of Elrond"),
                        Subfield(
                            code="b", value="IGNORE ME!"
                        ),  # there's no such thing as x11$b
                        Subfield(
                            code="n", value="(1 -"
                        ),  # regards section of work mentioned in t
                        Subfield(code="d", value="October TA 3018:"),
                        Subfield(code="c", value="Rivendell)"),
                        Subfield(
                            code="t", value="IGNORE ME!"
                        ),  # name of work, not the person
                        Subfield(
                            code="p", value="IGNORE ME!"
                        ),  # regards section of work mentioned in t
                        Subfield(code="q", value="IGNORE ME!"),
                        Subfield(code="l", value="Ayapeneco"),  # language of "t"
                        Subfield(code="e", value="key grip"),  # language of "t"
                        Subfield(code="e", value="best boy"),  # language of "t"
                    ],
                )
            ],
            code,
            primary,
            id=f"{code}",
        )
        for (code, primary) in [
            ("111", True),
            ("711", False),
        ]
    ],
    indirect=["marc_record"],
)
def test_meeting_contributor_all_fields(
    marc_record: Record, field_code: str, primary: bool
) -> None:
    # meetings are fundamentally different to Organisations and People, in
    # terms of the subfields they use - some have different meanings,
    # The most important one is $n, which refers to the meeting, whereas
    # it refers to a work by the agent in the other fields.
    contributor = transform_record(marc_record).data.contributors[0]
    assert contributor.roles == [Label(label="key grip"), Label(label="best boy")]
    label = "Council of Elrond (1 - October TA 3018: Rivendell)"
    assert contributor.primary == primary
    assert contributor.agent.label == label
    assert contributor.agent == Concept(
        type="Meeting",
        label=label,
        id=Identifiable(
            source_identifier=SourceIdentifier(
                identifier_type=Id(id="label-derived"),
                ontology_type="Meeting",
                value=normalise_identifier_value(label),
            ),
            other_identifiers=[],
        ),
    )
