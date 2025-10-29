from models.pipeline.identifier import Identifiable


def test_identifier_from_text_basic() -> None:
    ident = Identifiable.identifier_from_text(
        "History, bananas.", ontology_type="Concept"
    )
    assert ident.source_identifier.value == "history, bananas"
    assert ident.source_identifier.identifier_type.id == "label-derived"
    assert ident.source_identifier.ontology_type == "Concept"


def test_identifier_from_text_unicode_and_case() -> None:
    # Includes accented chars & mixed case; should be lowercased, accents removed.
    label = "Médecine Générale"
    ident = Identifiable.identifier_from_text(label, ontology_type="Concept")
    # Expected: 'medecine generale'
    assert ident.source_identifier.value == "medecine generale"


def test_identifier_from_text_ellipsis_preserved() -> None:
    ident = Identifiable.identifier_from_text("Something...", ontology_type="Concept")
    # Ellipsis is preserved (no trimming inside an ellipsis), matching Scala trimTrailingPeriod
    assert ident.source_identifier.value == "something..."


def test_identifier_from_text_truncation() -> None:
    base = "a" * 300
    ident = Identifiable.identifier_from_text(base, ontology_type="Concept")
    assert len(ident.source_identifier.value) == 255


def test_identifier_from_text_whitespace_and_internal_period() -> None:
    ident = Identifiable.identifier_from_text(
        "  Title. With Period.  ", ontology_type="Concept"
    )
    # Only the final trailing period is trimmed before lower & processing; internal periods remain.
    assert ident.source_identifier.value == "title. with period"


def test_identifier_from_text_removes_whitespace_after_non_ascii_filtered() -> None:
    # Mirrors Scala test: "Miki\u0107, \u017delimir \u0110." -> "mikic, zelimir"
    label = "Miki\u0107, \u017delimir \u0110."
    ident = Identifiable.identifier_from_text(label, ontology_type="Person")
    assert ident.source_identifier.value == "mikic, zelimir"


def test_identifier_from_text_specific_long_truncation_example() -> None:
    # Mirrors Scala truncation test with exact expected truncated output
    label = (
        "National Insurance Act, 1911 : explained, annotated and indexed, with appendices consisting of the Insurance Commissioners' "
        "official explanatory leaflets, Treasury regulations for the joint committee, tables of reserve values and voluntary contributions, "
        "regulations for procedure by the umpire, etc."
    )
    ident = Identifiable.identifier_from_text(label, ontology_type="Organisation")
    expected = (
        "national insurance act, 1911 : explained, annotated and indexed, with appendices consisting of the insurance commissioners' "
        "official explanatory leaflets, treasury regulations for the joint committee, tables of reserve values and voluntary contributions,"
    )
    assert ident.source_identifier.value == expected
