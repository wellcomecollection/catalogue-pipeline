import json

from test_utils import load_fixture

from transformers.loc.skos.raw_concept import RawLibraryOfCongressSKOSConcept


def test_label() -> None:
    """
    Label is extracted from madsrdf:authoritativeLabel
    """
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("skos_concept.json"))
    )
    assert concept.label == "Pickling"


class TestExclude:
    def test_exclude_no_graph(self) -> None:
        """
        If there is no graph, then the concept is to be excluded
        """
        concept = RawLibraryOfCongressSKOSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        assert concept.exclude() == True

    def test_exclude_no_matching_concept_node(self) -> None:
        """
        If the graph does not contain a node of type skos:Concept, it is to be excluded
        """
        concept = RawLibraryOfCongressSKOSConcept(
            json.loads(load_fixture("skos_deprecated_concept.json"))
        )
        assert concept.exclude() == True

    def test_do_not_exclude(self) -> None:
        """
        A complete, non-duplicate, non-deprecated record is to be included in the output
        """
        concept = RawLibraryOfCongressSKOSConcept(
            json.loads(load_fixture("skos_concept.json"))
        )
        assert concept.exclude() == False


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
