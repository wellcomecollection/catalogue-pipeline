"""Tests covering extraction of the primary language from MARC 008/35-37.

https://www.loc.gov/marc/bibliographic/bd008a.html
"""

from __future__ import annotations

import pytest
from pymarc.record import Field, Record

from adapters.transformers.marc.languages import extract_primary_language


def test_no_008_no_language(marc_record: Record) -> None:
    assert extract_primary_language(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="900716s1991    maub    ob    001 0 |||  "),)],
    indirect=True,
)
def test_no_attempt_to_code_language(marc_record: Record) -> None:
    assert extract_primary_language(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="900716s1991    maub    ob    001 0 aaa  "),)],
    indirect=True,
)
def test_unknown_language(marc_record: Record) -> None:
    assert extract_primary_language(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="900716s1991    maub    ob    001 0 lat  "),)],
    indirect=True,
)
def test_known_language(marc_record: Record) -> None:
    language = extract_primary_language(marc_record)
    assert language is not None
    assert language.id == "lat"
    assert language.label == "Latin"


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="980407c19909999caumr p o     0   a0mul c"),)],
    indirect=True,
)
def test_multiple_languages_code_is_suppressed(marc_record: Record) -> None:
    assert extract_primary_language(marc_record) is None
