import json

import pytest
from test_utils import load_fixture

from transformers.loc.raw_concept import RawLibraryOfCongressConcept


class TestSourceId:
    def test_remove_prefix_noop(self) -> None:
        """
        If there is no prefix to remove, remove_id_prefix will do nothing
        """
        assert (
            RawLibraryOfCongressConcept({"@id": "sh1234567890"}).source_id
            == "sh1234567890"
        )

    def test_remove_prefix_fully_qualified(self) -> None:
        """
        remove_id_prefix removes fully-qualified URL-style prefixes
        """
        assert (
            RawLibraryOfCongressConcept(
                {"@id": "http://id.loc.gov/authorities/subjects/sh1234567890"}
            ).source_id
            == "sh1234567890"
        )
        assert (
            RawLibraryOfCongressConcept(
                {"@id": "http://id.loc.gov/authorities/names/sh0987654321"}
            ).source_id
            == "sh0987654321"
        )

    def test_remove_prefix_relative(self) -> None:
        """
        remove_id_prefix removes relative/local prefixes
        """
        assert (
            RawLibraryOfCongressConcept(
                {"@id": "/authorities/subjects/sh1234567890"}
            ).source_id
            == "sh1234567890"
        )
        assert (
            RawLibraryOfCongressConcept(
                {"@id": "/authorities/names/sh0987654321"}
            ).source_id
            == "sh0987654321"
        )

    def test_remove_prefix_lookalikes(self) -> None:
        """
        remove_id_prefix only removes specific known prefixes,
        not just things that look a bit like them
        """
        assert (
            RawLibraryOfCongressConcept(
                {"@id": "/authorities/banana/sh1234567890"}
            ).source_id
            == "/authorities/banana/sh1234567890"
        )
        assert (
            RawLibraryOfCongressConcept(
                {"@id": "https://id.loc.gov.uk/authorities/subjects/sh1234567890"}
            ).source_id
            == "https://id.loc.gov.uk/authorities/subjects/sh1234567890"
        )


class TestSource:
    def test_source_subjects(self) -> None:
        """
        Given an id with the prefix /authorities/subjects/, the source will be lc-subjects
        """
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/subjects/sh2010105253"}
        )
        assert concept.source == "lc-subjects"

    def test_source_names(self) -> None:
        """
        Given an id with the prefix /authorities/subjects/, the source will be lc-subjects
        """
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/names/sh2010105253"}
        )
        assert concept.source == "lc-names"

    def test_source_invalid(self) -> None:
        with pytest.raises(ValueError):
            concept = RawLibraryOfCongressConcept(
                {"@id": "authorities/childrensSubjects/sj2021051581"}
            )
            _ = concept.source


class TestExclusion:
    def test_do_not_exclude(self) -> None:
        """
        A record with a corresponding node in its internal graph, and which is not a duplicate,
        should be included in the output
        """
        concept = RawLibraryOfCongressConcept(
            {"@id": "authorities/names/sh2010105253", "@graph": []}
        )
        # The SUT at this point doesn't actually care what the node is, just that it exists
        concept._raw_concept_node = {}
        assert concept.exclude() is False

    def test_exclude_no_node(self) -> None:
        """
        If a record does not contain a corresponding node in its internal graph
        then it should be excluded
        """
        concept = RawLibraryOfCongressConcept(
            {"@id": "authorities/names/sh2010105253", "@graph": []}
        )
        assert concept.exclude()

    def test_exclude_marked_duplicates(self) -> None:
        """
        If a record's identifier is suffixed with -781, this marks the entry as a duplicate
        which is to be excluded
        """
        concept = RawLibraryOfCongressConcept(
            {"@id": "authorities/names/sh2010105253-781", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.exclude()


class TestGeographic:
    def test_is_geographic(self) -> None:
        """
        A concept is geographic if its @type list contains madsrdf:Geographic or http://id.loc.gov/datatypes/codes/gac"
        """
        concept = RawLibraryOfCongressConcept(
            json.loads(load_fixture("loc/mads_geographic_concept.json"))
        )
        assert concept.is_geographic

    def test_is_not_geographic(self) -> None:
        concept = RawLibraryOfCongressConcept(
            json.loads(load_fixture("loc/mads_concept.json"))
        )
        assert concept.is_geographic is False


def test_label() -> None:
    """
    Label is extracted from madsrdf:authoritativeLabel
    """
    concept = RawLibraryOfCongressConcept(
        json.loads(load_fixture("loc/mads_concept.json"))
    )
    assert concept.label == "Stump work"


class TestBroaderConcepts:
    def test_real_example(self) -> None:
        concept = RawLibraryOfCongressConcept(
            json.loads(load_fixture("loc/mads_geographic_concept.json"))
        )
        assert concept.broader_concept_ids == ["sh85040229", "sh85053109", "sh92006359"]

    def test_none(self) -> None:
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.broader_concept_ids == []

    def test_single(self) -> None:
        concept = RawLibraryOfCongressConcept(
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
        concept = RawLibraryOfCongressConcept(
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
        concept = RawLibraryOfCongressConcept(
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
        concept = RawLibraryOfCongressConcept(
            json.loads(load_fixture("loc/mads_related_concept.json"))
        )
        assert concept.related_concept_ids == ["sh90003066"]

    def test_none(self) -> None:
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.related_concept_ids == []

    def test_single(self) -> None:
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasReciprocalAuthority": {
                "@id": "http://id.loc.gov/authorities/subjects/sh90003066"
            }
        }
        assert concept.related_concept_ids == ["sh90003066"]

    def test_multiple(self) -> None:
        concept = RawLibraryOfCongressConcept(
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
        concept = RawLibraryOfCongressConcept(
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
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {}
        assert concept.narrower_concept_ids == []

    def test_get_narrowers_from_components(self) -> None:
        concept = RawLibraryOfCongressConcept(
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
        concept = RawLibraryOfCongressConcept(
            {"@id": "/authorities/subjects/sh2010105253", "@graph": []}
        )
        concept._raw_concept_node = {
            "madsrdf:hasNarrowerAuthority": {
                "@id": "http://id.loc.gov/authorities/subjects/sh00000029"
            }
        }
        assert concept.narrower_concept_ids == ["sh00000029"]

    def test_get_narrowers_from_both(self) -> None:
        concept = RawLibraryOfCongressConcept(
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


def test_alternative_labels() -> None:
    concept = RawLibraryOfCongressConcept(
        json.loads(load_fixture("loc/mads_related_concept.json"))
    )
    assert set(concept.alternative_labels) == {
        "Loop blocking (Computer science)",
        "Blocking, Loop (Computer science)",
        "Tiling, Loop (Computer science)",
    }
