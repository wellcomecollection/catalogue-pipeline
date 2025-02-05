import json

from test_utils import load_fixture

from transformers.loc.raw_concept import RawLibraryOfCongressSKOSConcept


def test_label() -> None:
    """
    Label is extracted from skos:prefLabel
    """
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("skos_concept.json"))
    )
    assert concept.label == "Pickling"


class TestGeographic:
    def test_is_geographic(self) -> None:
        """
        A concept is geographic if there exists skos:notation with a gac type
        """
        concept = RawLibraryOfCongressSKOSConcept(
            json.loads(load_fixture("skos_geographic_concept.json"))
        )
        assert concept.is_geographic == True

    def test_is_not_geographic(self) -> None:
        concept = RawLibraryOfCongressSKOSConcept(
            json.loads(load_fixture("skos_concept.json"))
        )
        assert concept.is_geographic == False


def test_broader_concepts() -> None:
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("skos_geographic_concept.json"))
    )
    assert concept.broader_concept_ids == ["sh85040229", "sh85053109", "sh92006359"]
