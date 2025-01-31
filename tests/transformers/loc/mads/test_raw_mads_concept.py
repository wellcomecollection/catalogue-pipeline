import json

from test_utils import load_fixture

from transformers.loc.mads.raw_concept import RawLibraryOfCongressMADSConcept

sh2010105253 = json.loads(load_fixture("mads_composite_concept.json"))


def test_exclude_no_graph() -> None:
    """
    If there is no graph, then the concept is to be excluded
    """
    concept = RawLibraryOfCongressMADSConcept(
        {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
    )
    assert concept.exclude() == True


def test_exclude_no_matching_concept_node() -> None:
    """
    If the graph does not contain a node of type skos:Concept, it is to be excluded
    """
    concept = RawLibraryOfCongressMADSConcept(
        json.loads(load_fixture("mads_deprecated_concept.json"))
    )
    assert concept.exclude() == True


def test_do_not_exclude() -> None:
    """
    A complete, non-duplicate, non-deprecated record is to be included in the output
    """
    concept = RawLibraryOfCongressMADSConcept(
        json.loads(load_fixture("mads_concept.json"))
    )
    assert concept.exclude() == False


def test_label() -> None:
    """
    Label is extracted from madsrdf:authoritativeLabel
    """
    concept = RawLibraryOfCongressMADSConcept(
        json.loads(load_fixture("mads_concept.json"))
    )
    assert concept.label == "Stump work"
