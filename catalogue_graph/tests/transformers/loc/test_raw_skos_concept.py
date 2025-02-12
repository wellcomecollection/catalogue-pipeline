import json

from test_utils import load_fixture

from transformers.loc.raw_concept import RawLibraryOfCongressSKOSConcept


def test_label() -> None:
    """
    Label is extracted from skos:prefLabel
    """
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("loc/skos_concept.json"))
    )
    assert concept.label == "Pickling"


def test_broader_concepts() -> None:
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("loc/skos_geographic_concept.json"))
    )
    assert concept.broader_concept_ids == ["sh85040229", "sh85053109", "sh92006359"]
