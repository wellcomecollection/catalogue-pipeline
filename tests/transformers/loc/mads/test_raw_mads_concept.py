import json

from test_utils import load_fixture

from transformers.loc.mads.raw_concept import RawLibraryOfCongressMADSConcept

sh2010105253 = json.loads(load_fixture("mads_composite_concept.json"))


def test_source_id() -> None:
    """
    source_id is derived from the @id property in the source data.
    It is the unqualified version of the full id
    """
    concept = RawLibraryOfCongressMADSConcept(
        {"@id": "/authorities/subjects/sh2010105253"}
    )
    assert concept.source_id == "sh2010105253"


def test_source() -> None:
    """
    source is discovered by examining the prefix to the id.
    """
    concept = RawLibraryOfCongressMADSConcept(
        {"@id": "/authorities/subjects/sh2010105253"}
    )
    assert concept.source == "lc-subjects"
