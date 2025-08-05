from test_utils import load_json_fixture
from ingestor.models.concept import RawNeptuneConcept
from ingestor.transformers.concept_override import ConceptTextOverrider
import io
from ingestor.models.indexable_concept import ConceptDescription


def test_unchanged_if_not_mentioned() -> None:
    """
    Overrides are only applied to concepts that are present in the override CSV
    """
    concept = RawNeptuneConcept(load_json_fixture(
        "neptune/concept_query_single.json"
    ), {})
    overrider = ConceptTextOverrider(io.StringIO("""id,label,description
        xx, yy, zz
        """))
    assert overrider.display_label_of(concept) == concept.label
    assert overrider.description_of(concept) == concept.description


def test_label_unchanged_if_unset() -> None:
    """
    Leaving the label blank signals that the label from the
    source concept should be used as-is
    """
    concept = RawNeptuneConcept(load_json_fixture(
        "neptune/concept_query_single.json"
    ), {})
    overrider = ConceptTextOverrider(
        io.StringIO("""id,label,description
        id, , Pottery with a transparent jade green glaze
        """)
    )

    assert overrider.display_label_of(concept) == concept.label
    assert overrider.description_of(concept) == ConceptDescription(
        text='Pottery with a transparent jade green glaze',
        sourceLabel='wellcome',
        sourceUrl=''
    )


def test_description_unchanged_if_unset() -> None:
    """
    Leaving the description blank signals that the description from the
    source concept should be used as-is
    """
    concept = RawNeptuneConcept(load_json_fixture(
        "neptune/concept_query_single.json"
    ), {})
    overrider = ConceptTextOverrider(
        io.StringIO("""id,label,description
        id, Celadon Ware,
        """)
    )

    assert overrider.display_label_of(concept) == "Celadon Ware"
    assert overrider.description_of(concept) == concept.description


def test_description_removed_if_explicit_empty() -> None:
    """
    Populating the description field with just the word, "empty"
    signals that the concept should have no description, regardless of
    whether one is found in the source concepts
    """
    concept = RawNeptuneConcept(load_json_fixture(
        "neptune/concept_query_single.json"
    ), {})
    overrider = ConceptTextOverrider(
        io.StringIO("""id,label,description
        id, , empty
        """)
    )

    assert overrider.display_label_of(concept) == concept.label
    assert overrider.description_of(concept) is None


def test_change_label_and_description() -> None:
    """
    Populating the description field with just the word, "empty"
    signals that the concept should have no description, regardless of
    whether one is found in the source concepts
    """
    concept = RawNeptuneConcept(load_json_fixture(
        "neptune/concept_query_single.json"
    ), {})
    overrider = ConceptTextOverrider(
        io.StringIO("""id,label,description
        id, New Label, New Description
        """)
    )

    assert overrider.display_label_of(concept) == "New Label"
    assert overrider.description_of(concept).text == "New Description"
