import io

import pytest
from test_utils import load_json_fixture

from ingestor.models.display.id_label import DisplayIdLabel
from ingestor.models.display.location import DisplayDigitalLocation
from ingestor.models.indexable_concept import ConceptDescription
from ingestor.transformers.concept_override import ConceptTextOverrideProvider
from ingestor.transformers.raw_concept import RawNeptuneConcept


@pytest.fixture
def concept() -> RawNeptuneConcept:
    return RawNeptuneConcept(load_json_fixture("neptune/concept_query_single.json"))


def test_unchanged_if_not_mentioned(concept: RawNeptuneConcept) -> None:
    """
    Overrides are only applied to concepts that are present in the override CSV
    """
    overrider = ConceptTextOverrideProvider(
        io.StringIO("""id,label,description, image_url
        xx, yy, zz, www
        """)
    )
    assert overrider.display_label_of(concept) == concept.display_label
    assert overrider.description_of(concept) == concept.description
    assert overrider.display_image(concept) == []


def test_label_unchanged_if_unset(concept: RawNeptuneConcept) -> None:
    """
    Leaving the label blank signals that the label from the
    source concept should be used as-is
    """
    overrider = ConceptTextOverrideProvider(
        io.StringIO("""id,label,description,image_url
        id, , Pottery with a transparent jade green glaze, 
        """)
    )

    assert overrider.display_label_of(concept) == concept.display_label
    assert overrider.description_of(concept) == ConceptDescription(
        text="Pottery with a transparent jade green glaze",
        sourceLabel=None,
        sourceUrl=None,
    )


def test_description_unchanged_if_unset(concept: RawNeptuneConcept) -> None:
    """
    Leaving the description blank signals that the description from the
    source concept should be used as-is
    """
    overrider = ConceptTextOverrideProvider(
        io.StringIO("""id,label,description,image_url
        id, Celadon Ware, , ,
        """)
    )

    assert overrider.display_label_of(concept) == "Celadon Ware"
    assert overrider.description_of(concept) == concept.description


def test_description_removed_if_explicit_empty(concept: RawNeptuneConcept) -> None:
    """
    Populating the description field with just the word, "empty"
    signals that the concept should have no description, regardless of
    whether one is found in the source concepts
    """
    overrider = ConceptTextOverrideProvider(
        io.StringIO("""id,label,description, image_url
        id, , empty, 
        """)
    )

    assert overrider.display_label_of(concept) == concept.display_label
    assert overrider.description_of(concept) is None


def test_change_label_and_description(concept: RawNeptuneConcept) -> None:
    """
    Populating the description field with just the word, "empty"
    signals that the concept should have no description, regardless of
    whether one is found in the source concepts
    """
    overrider = ConceptTextOverrideProvider(
        io.StringIO("""id,label,description, image_url
        id, New Label, New Description,
        """)
    )

    assert overrider.display_label_of(concept) == "New Label"
    assert overrider.description_of(concept).text == "New Description"  # type: ignore


def test_add_display_image(concept: RawNeptuneConcept) -> None:
    """
    Populating the image_url field with a IIIF info.json URL
    signals that the concept should have a display image
    """
    overrider = ConceptTextOverrideProvider(
        io.StringIO("""id,label,description,image_url
        id, , , www.cat_surgery.info.json
        """)
    )

    assert overrider.display_image(concept) == [
        DisplayDigitalLocation(
            url="www.cat_surgery.info.json",
            locationType=DisplayIdLabel(
                id="iiif-image", label="IIIF Image", type="LocationType"
            ),
            accessConditions=[],
        )
    ]
