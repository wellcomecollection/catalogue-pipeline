"""Port of MarcHasRecordControlNumberTest.scala and the identifier cases of SierraConceptsTest.scala."""

import pytest
from pymarc.record import Field, Indicators, Subfield
from structlog.testing import capture_logs

from adapters.transformers.marc.authority_standard_number import extract_identifier
from models.pipeline.identifier import SourceIdentifier


def _field(indicator2: str, *identifier_values: str) -> Field:
    return Field(
        tag="655",
        indicators=Indicators(" ", indicator2),
        subfields=[Subfield(code="0", value=value) for value in identifier_values],
    )


def _source_identifier(
    field: Field, ontology_type: str = "Concept"
) -> SourceIdentifier:
    identifiable = extract_identifier(field, ontology_type)
    assert identifiable is not None
    return identifiable.source_identifier


def test_finds_an_lcsh_identifier() -> None:
    identifier = _source_identifier(_field("0", "sh2009124405"))
    assert identifier.identifier_type.id == "lc-subjects"
    assert identifier.value == "sh2009124405"
    assert identifier.ontology_type == "Concept"


def test_finds_an_lc_names_identifier() -> None:
    identifier = _source_identifier(_field("0", "n84165387"))
    assert identifier.identifier_type.id == "lc-names"
    assert identifier.value == "n84165387"


def test_finds_an_identifier_with_a_loc_url_prefix() -> None:
    identifier = _source_identifier(
        _field("0", "http://idlocgov/authorities/subjects/sh92000896")
    )
    assert identifier.identifier_type.id == "lc-subjects"
    assert identifier.value == "sh92000896"


def test_finds_an_identifier_with_an_nlm_url_prefix() -> None:
    identifier = _source_identifier(_field("2", "https://id.nlm.nih.gov/mesh/D049671"))
    assert identifier.identifier_type.id == "nlm-mesh"
    assert identifier.value == "D049671"


def test_strips_dnlm_prefix() -> None:
    identifier = _source_identifier(_field("2", "(DNLM)D049671"))
    assert identifier.identifier_type.id == "nlm-mesh"
    assert identifier.value == "D049671"


@pytest.mark.parametrize("value", ["D000934", "sj97002429", "shsh85100861"])
def test_an_invalid_loc_identifier_is_logged_and_dropped(value: str) -> None:
    """The Scala throws here; the Python logs an error and leaves the concept label-derived."""
    with capture_logs() as logs:
        assert extract_identifier(_field("0", value), "Concept") is None
    assert [
        entry["value"]
        for entry in logs
        if entry["event"] == "Could not determine LoC scheme from identifier"
    ] == [value]


def test_finds_a_mesh_identifier() -> None:
    identifier = _source_identifier(_field("2", "mesh/456"))
    assert identifier.identifier_type.id == "nlm-mesh"
    assert identifier.value == "mesh/456"


def test_finds_no_identifier_if_indicator_2_is_4() -> None:
    assert extract_identifier(_field("4", "noid/000"), "Concept") is None


def test_finds_no_identifier_if_indicator_2_is_empty() -> None:
    assert extract_identifier(_field(" ", "lcsh/789"), "Concept") is None


def test_finds_no_identifier_for_an_unrecognised_scheme() -> None:
    assert extract_identifier(_field("8", "u/xxx"), "Concept") is None


def test_passes_through_the_ontology_type() -> None:
    identifier = _source_identifier(_field("2", "mesh/456"), ontology_type="Item")
    assert identifier.ontology_type == "Item"


def test_normalises_and_deduplicates_identifiers() -> None:
    identifier = _source_identifier(
        _field(
            "0",
            "sh96010159",
            "sh96010159",
            "(DNLM)sh96010159",
            "sh96010159.",
            "sh 96010159",
            "https://id.nlm.nih.gov/mesh/sh96010159",
        )
    )
    assert identifier.identifier_type.id == "lc-subjects"
    assert identifier.value == "sh96010159"


def test_multiple_different_identifiers_give_no_identifier() -> None:
    assert extract_identifier(_field("0", "lcsh/xxx", "lcsh/yyy"), "Concept") is None


@pytest.mark.parametrize("indicator2", [" ", "1", "3", "4", "5", "6", "7"])
def test_ignores_identifiers_in_unknown_schemes(indicator2: str) -> None:
    assert extract_identifier(_field(indicator2, "dunno/xxx"), "Concept") is None


@pytest.mark.parametrize("indicator2", [" ", "0", "1", "2", "3", "4", "5", "6", "7"])
def test_no_subfield_0_gives_no_identifier_regardless_of_scheme(
    indicator2: str,
) -> None:
    assert extract_identifier(_field(indicator2), "Concept") is None
