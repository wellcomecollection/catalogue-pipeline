import pytest

from adapters.transformers.marc.concepts import label_derived_identifier
from models.pipeline.identifier import Identifiable


@pytest.mark.parametrize(
    ("label", "expected"),
    [
        ("MDCCLXXXVII. [1787]", "1787"),
        ("1851 Nov. 27.", "1851 nov 27"),
        ("27.04.2018-30.10.2018", "27042018-30102018"),
        ("To 1763 (New France)", "to 1763 new france"),
        ('"1547" [i.e. 1788]', "1547 ie 1788"),
        ("[1958?]", "1958"),
        ("©1958", "1958"),
        ("between 1900 and 1910", "1900 and 1910"),
        ("fl. 1620-1650", "1620-1650"),
        ("floruit 1620", "1620"),
        ("fl 1620", "1620"),
        ("2000 A.D.", "2000 ad"),
        ("One Million Years B.C.", "one million years bc"),
        ("ca. 1066", "ca 1066"),
        # "fl" inside a word is not an abbreviation, so the word keeps its letters
        ("Influenza Epidemic, 1918-1919.", "influenza epidemic, 1918-1919"),
        ("Flavians, 69-96.", "flavians, 69-96"),
    ],
)
def test_period_id_strips_punctuation_qualifiers_and_roman_numerals(
    label: str, expected: str
) -> None:
    identifier = label_derived_identifier(label, "Period")
    assert isinstance(identifier, Identifiable)
    assert identifier.source_identifier.value == expected


@pytest.mark.parametrize(
    ("label", "expected"),
    [
        ("MDCCLXXXVII.", "mdcclxxxvii"),
        ("MDCC", "mdcc"),
        ("[?]", "[?]"),
        ("fl.", "fl"),
    ],
)
def test_period_id_falls_back_to_the_label_when_nothing_is_left(
    label: str, expected: str
) -> None:
    identifier = label_derived_identifier(label, "Period")
    assert isinstance(identifier, Identifiable)
    assert identifier.source_identifier.value == expected
