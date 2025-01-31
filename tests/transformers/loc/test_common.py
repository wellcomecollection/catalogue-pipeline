import pytest
from typing import Type
from transformers.loc.common import remove_id_prefix, RawLibraryOfCongressConcept
from transformers.loc.mads.raw_concept import RawLibraryOfCongressMADSConcept
from transformers.loc.skos.raw_concept import RawLibraryOfCongressSKOSConcept


class TestRemovePrefix:
    def test_remove_prefix_noop(self) -> None:
        """
        If there is no prefix to remove, remove_id_prefix will do nothing
        """
        assert remove_id_prefix("sh1234567890") == "sh1234567890"


    def test_remove_prefix_fully_qualified(self) -> None:
        """
        remove_id_prefix removes fully-qualified URL-style prefixes
        """
        assert (
            remove_id_prefix("http://id.loc.gov/authorities/subjects/sh1234567890")
            == "sh1234567890"
        )
        assert (
            remove_id_prefix("http://id.loc.gov/authorities/names/sh0987654321")
            == "sh0987654321"
        )


    def test_remove_prefix_relative(self) -> None:
        """
        remove_id_prefix removes relative/local prefixes
        """
        assert remove_id_prefix("/authorities/subjects/sh1234567890") == "sh1234567890"
        assert remove_id_prefix("/authorities/names/sh0987654321") == "sh0987654321"


    def test_remove_prefix_lookalikes(self) -> None:
        """
        remove_id_prefix only removes specific known prefixes,
        not just things that look a bit like them
        """
        assert (
            remove_id_prefix("/authorities/banana/sh1234567890")
            == "/authorities/banana/sh1234567890"
        )
        assert (
            remove_id_prefix("https://id.loc.gov.uk/authorities/subjects/sh1234567890")
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
        with (pytest.raises(ValueError)):
            concept = RawLibraryOfCongressConcept(
                {"@id": "authorities/childrensSubjects/sj2021051581"}
            )
            concept.source


@pytest.mark.parametrize("sut_class", [RawLibraryOfCongressSKOSConcept, RawLibraryOfCongressMADSConcept])
class TestExclusion:
    def test_do_not_exclude(self, sut_class:Type[RawLibraryOfCongressConcept])->None:
        """
        A record with a corresponding node in its internal graph, and which is not a duplicate,
        should be included in the output
        """
        concept = sut_class( {"@id": "authorities/names/sh2010105253", "@graph":[]})
        # The SUT at this point doesn't actually care what the node is, just that it exists
        concept._raw_concept_node = {}
        assert concept.exclude() == False

    def test_exclude_no_node(self, sut_class:Type[RawLibraryOfCongressConcept])->None:
        """
        If a record does not contain a corresponding node in its internal graph
        then it should be excluded
        """
        concept = sut_class( {"@id": "authorities/names/sh2010105253", "@graph":[]})
        assert concept.exclude() == True

    def test_exclude_marked_duplicates(self, sut_class:Type[RawLibraryOfCongressConcept]) -> None:
        """
        If a record's identifier is suffixed with -781, this marks the entry as a duplicate
        which is to be excluded
        """
        concept = sut_class( {"@id": "authorities/names/sh2010105253-781", "@graph":[]})
        concept._raw_concept_node = {}
        assert concept.exclude() == True

