from adapters.ebsco.transformers.text_utils import (
    clean_concept_label,
    normalise_identifier_value,
)


def test_clean_concept_label_trims_punctuation_and_whitespace() -> None:
    assert clean_concept_label("Label.;:  ") == "Label"
    assert clean_concept_label("Label") == "Label"
    assert clean_concept_label("  Label.") == "Label"


def test_normalise_identifier_value_collapses_whitespace_and_lowercases() -> None:
    assert normalise_identifier_value("  Mixed   Case  Label ") == "mixed case label"
    assert normalise_identifier_value("SINGLE") == "single"
