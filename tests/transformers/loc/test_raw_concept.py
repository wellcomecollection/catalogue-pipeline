from typing import Type

import pytest

from transformers.loc.raw_concept import (
    RawLibraryOfCongressConcept,
    RawLibraryOfCongressMADSConcept,
    RawLibraryOfCongressSKOSConcept,
)


@pytest.mark.parametrize(
    "sut_class", [RawLibraryOfCongressSKOSConcept, RawLibraryOfCongressMADSConcept]
)
class TestSourceId:
    def test_remove_prefix_noop(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        If there is no prefix to remove, remove_id_prefix will do nothing
        """
        assert sut_class({"@id": "sh1234567890"}).source_id == "sh1234567890"

    def test_remove_prefix_fully_qualified(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        remove_id_prefix removes fully-qualified URL-style prefixes
        """
        assert (
            sut_class(
                {"@id": "http://id.loc.gov/authorities/subjects/sh1234567890"}
            ).source_id
            == "sh1234567890"
        )
        assert (
            sut_class(
                {"@id": "http://id.loc.gov/authorities/names/sh0987654321"}
            ).source_id
            == "sh0987654321"
        )

    def test_remove_prefix_relative(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        remove_id_prefix removes relative/local prefixes
        """
        assert (
            sut_class({"@id": "/authorities/subjects/sh1234567890"}).source_id
            == "sh1234567890"
        )
        assert (
            sut_class({"@id": "/authorities/names/sh0987654321"}).source_id
            == "sh0987654321"
        )

    def test_remove_prefix_lookalikes(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        remove_id_prefix only removes specific known prefixes,
        not just things that look a bit like them
        """
        assert (
            sut_class({"@id": "/authorities/banana/sh1234567890"}).source_id
            == "/authorities/banana/sh1234567890"
        )
        assert (
            sut_class(
                {"@id": "https://id.loc.gov.uk/authorities/subjects/sh1234567890"}
            ).source_id
            == "https://id.loc.gov.uk/authorities/subjects/sh1234567890"
        )


@pytest.mark.parametrize(
    "sut_class", [RawLibraryOfCongressSKOSConcept, RawLibraryOfCongressMADSConcept]
)
class TestSource:
    def test_source_subjects(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        Given an id with the prefix /authorities/subjects/, the source will be lc-subjects
        """
        concept = sut_class({"@id": "/authorities/subjects/sh2010105253"})
        assert concept.source == "lc-subjects"

    def test_source_names(self, sut_class: Type[RawLibraryOfCongressConcept]) -> None:
        """
        Given an id with the prefix /authorities/subjects/, the source will be lc-subjects
        """
        concept = sut_class({"@id": "/authorities/names/sh2010105253"})
        assert concept.source == "lc-names"

    def test_source_invalid(self, sut_class: Type[RawLibraryOfCongressConcept]) -> None:
        with pytest.raises(ValueError):
            concept = sut_class({"@id": "authorities/childrensSubjects/sj2021051581"})
            concept.source


@pytest.mark.parametrize(
    "sut_class", [RawLibraryOfCongressSKOSConcept, RawLibraryOfCongressMADSConcept]
)
class TestExclusion:
    def test_do_not_exclude(self, sut_class: Type[RawLibraryOfCongressConcept]) -> None:
        """
        A record with a corresponding node in its internal graph, and which is not a duplicate,
        should be included in the output
        """
        concept = sut_class({"@id": "authorities/names/sh2010105253", "@graph": []})
        # The SUT at this point doesn't actually care what the node is, just that it exists
        concept._raw_concept_node = {}
        assert concept.exclude() == False

    def test_exclude_no_node(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        If a record does not contain a corresponding node in its internal graph
        then it should be excluded
        """
        concept = sut_class({"@id": "authorities/names/sh2010105253", "@graph": []})
        assert concept.exclude() == True

    def test_exclude_marked_duplicates(
        self, sut_class: Type[RawLibraryOfCongressConcept]
    ) -> None:
        """
        If a record's identifier is suffixed with -781, this marks the entry as a duplicate
        which is to be excluded
        """
        concept = sut_class({"@id": "authorities/names/sh2010105253-781", "@graph": []})
        concept._raw_concept_node = {}
        assert concept.exclude() == True
