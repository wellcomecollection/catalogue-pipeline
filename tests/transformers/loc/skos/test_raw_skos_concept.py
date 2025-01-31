import json

from test_utils import load_fixture

from transformers.loc.skos.raw_concept import RawLibraryOfCongressSKOSConcept


def test_exclude_no_graph() -> None:
    """
    If there is no graph, then the concept is to be excluded
    """
    concept = RawLibraryOfCongressSKOSConcept(
        {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
    )
    assert concept.exclude() == True


def test_exclude_no_matching_concept_node() -> None:
    """
    If the graph does not contain a node of type skos:Concept, it is to be excluded
    """
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("skos_deprecated_concept.json"))
    )
    assert concept.exclude() == True


def test_do_not_exclude() -> None:
    """
    A complete, non-duplicate, non-deprecated record is to be included in the output
    """
    concept = RawLibraryOfCongressSKOSConcept(
        json.loads(load_fixture("skos_concept.json"))
    )
    assert concept.exclude() == False
