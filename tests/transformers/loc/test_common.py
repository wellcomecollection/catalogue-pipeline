import pytest

from transformers.loc.common import remove_id_prefix, RawLibraryOfCongressConcept

def test_remove_prefix_noop() -> None:
    """
    If there is no prefix to remove, remove_id_prefix will do nothing
    """
    assert remove_id_prefix("sh1234567890") == "sh1234567890"


def test_remove_prefix_fully_qualified() -> None:
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


def test_remove_prefix_relative() -> None:
    """
    remove_id_prefix removes relative/local prefixes
    """
    assert remove_id_prefix("/authorities/subjects/sh1234567890") == "sh1234567890"
    assert remove_id_prefix("/authorities/names/sh0987654321") == "sh0987654321"


def test_remove_prefix_lookalikes() -> None:
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


def test_source_subjects() -> None:
    """
    Given an id with the prefix /authorities/subjects/, the source will be lc-subjects
    """
    concept = RawLibraryOfCongressConcept(
        {"@id": "/authorities/subjects/sh2010105253"}
    )
    assert concept.source == "lc-subjects"


def test_source_names() -> None:
    """
    Given an id with the prefix /authorities/subjects/, the source will be lc-subjects
    """
    concept = RawLibraryOfCongressConcept(
        {"@id": "/authorities/names/sh2010105253"}
    )
    assert concept.source == "lc-names"


def test_source_invalid() -> None:
    with (pytest.raises(ValueError)):
        concept = RawLibraryOfCongressConcept(
            {"@id": "authorities/childrensSubjects/sj2021051581"}
        )
        concept.source
