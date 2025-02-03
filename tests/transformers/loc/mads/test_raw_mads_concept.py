import json

from test_utils import load_fixture

from transformers.loc.mads.raw_concept import RawLibraryOfCongressMADSConcept


def test_label() -> None:
    """
    Label is extracted from madsrdf:authoritativeLabel
    """
    concept = RawLibraryOfCongressMADSConcept(
        json.loads(load_fixture("mads_concept.json"))
    )
    assert concept.label == "Stump work"


class TestExclude:
    def test_exclude_no_graph(self) -> None:
        """
        If there is no graph, then the concept is to be excluded
        """
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        assert concept.exclude() == True

    def test_exclude_no_matching_concept_node(self) -> None:
        """
        If the graph does not contain a node of type skos:Concept, it is to be excluded
        """
        concept = RawLibraryOfCongressMADSConcept(
            json.loads(load_fixture("mads_deprecated_concept.json"))
        )
        assert concept.exclude() == True

    def test_do_not_exclude(self) -> None:
        """
        A complete, non-duplicate, non-deprecated record is to be included in the output
        """
        concept = RawLibraryOfCongressMADSConcept(
            json.loads(load_fixture("mads_concept.json"))
        )
        assert concept.exclude() == False


class TestGeographic:
    def test_is_geographic(self) -> None:
        """
        A concept is geographic if its @type list contains madsrdf:Geographic
        """
        concept = RawLibraryOfCongressMADSConcept(
            json.loads(load_fixture("mads_geographic_concept.json"))
        )
        assert concept.is_geographic == True

    def test_is_not_geographic(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            json.loads(load_fixture("mads_concept.json"))
        )
        assert concept.is_geographic == False


class TestBroaderConcepts:
    def test_real_example(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            json.loads(load_fixture("mads_geographic_concept.json"))
        )
        assert concept.broader_concept_ids == ["sh85040229", "sh85053109", "sh92006359"]

    def test_none(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.broader_concept_ids == []

    def test_single(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        # exaample from sh00000014, Stuffed foods (Cooking)
        concept._raw_concept_node = {
            "madsrdf:hasBroaderAuthority": {
                "@id": "http://id.loc.gov/authorities/subjects/sh85129334"
            }
        }
        assert concept.broader_concept_ids == ["sh85129334"]

    def test_multiple(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasBroaderAuthority": [
                {"@id": "http://id.loc.gov/authorities/subjects/sh85129334"},
                {"@id": "http://id.loc.gov/authorities/subjects/sh85068533"},
            ]
        }
        assert concept.broader_concept_ids == ["sh85129334", "sh85068533"]

    def test_ignore_underscore_n(self) -> None:
        # _:nbunchanumbers identifiers are to be ignored.
        # example from /authorities/subjects/sh00008764, Bintan Island (Indonesia)
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasBroaderAuthority": [
                {"@id": "_:n428e364baf3942ff9c026b0033bac3d0b5"},
                {"@id": "http://id.loc.gov/authorities/subjects/sh85068533"},
            ]
        }
        assert concept.broader_concept_ids == ["sh85068533"]


class TestRelatedConcepts:
    def test_real_example(self) -> None:
        # A real-world example, taken directly from the export,
        # This helps to give confidence that the whole test isn't just
        # passing due to a bogus assumption when making artificial test data.
        concept = RawLibraryOfCongressMADSConcept(
            json.loads(load_fixture("mads_related_concept.json"))
        )
        assert concept.related_concept_ids == ["sh90003066"]

    def test_none(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.related_concept_ids == []

    def test_single(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasReciprocalAuthority": {
                "@id": "http://id.loc.gov/authorities/subjects/sh90003066"
            }
        }
        assert concept.related_concept_ids == ["sh90003066"]

    def test_multiple(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasReciprocalAuthority": [
                {"@id": "http://id.loc.gov/authorities/subjects/sh123456789"},
                {"@id": "http://id.loc.gov/authorities/subjects/sh987654321"},
            ]
        }
        assert concept.related_concept_ids == ["sh123456789", "sh987654321"]

    def test_ignore_underscore_n(self) -> None:
        # _:nbunchanumbers identifiers are to be ignored.
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasReciprocalAuthority": [
                {"@id": "_:n428e364baf3942ff9c026b0033bac3d0b5"},
                {"@id": "http://id.loc.gov/authorities/subjects/sh123456789"},
            ]
        }
        assert concept.related_concept_ids == ["sh123456789"]


class TestNarrower:

    def test_get_no_narrowers(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.narrower_concept_ids == []

    def test_get_narrowers_from_components(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:componentList": {
                "@list": [
                    {"@id": "http://id.loc.gov/authorities/subjects/sh85098685"},
                    {"@id": "http://id.loc.gov/authorities/subjects/sh99001366"},
                ]
            },
        }
        assert concept.narrower_concept_ids == ["sh85098685", "sh99001366"]

    def test_get_narrowers_from_narrower_authority(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasNarrowerAuthority": {
                "@id": "http://id.loc.gov/authorities/subjects/sh00000029"
            }
        }
        assert concept.narrower_concept_ids == ["sh00000029"]

    def test_get_narrowers_from_both(self) -> None:
        concept = RawLibraryOfCongressMADSConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:componentList": {
                "@list": [
                    {"@id": "http://id.loc.gov/authorities/subjects/sh85098685"},
                    {"@id": "http://id.loc.gov/authorities/subjects/sh99001366"},
                ]
            },
            "madsrdf:hasNarrowerAuthority": {
                "@id": "http://id.loc.gov/authorities/subjects/sh00000029"
            },
        }
        assert set(concept.narrower_concept_ids) == {
            "sh00000029",
            "sh85098685",
            "sh99001366",
        }
