import pytest
from pymarc.record import Field, Record

from models.pipeline.id_label import Language

from ..helpers import lone_element
from .ebsco_test_transformer import transform_ebsco_record


def _get_languages(marc_record: Record) -> list[Language]:
    return transform_ebsco_record(marc_record).data.languages


def test_no_008_no_language(marc_record: Record) -> None:
    assert _get_languages(marc_record) == []


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="900716s1991    maub    ob    001 0 |||  "),)],
    indirect=True,
)
def test_no_attempt_to_code_language(marc_record: Record) -> None:
    assert _get_languages(marc_record) == []


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="900716s1991    maub    ob    001 0 aaa  "),)],
    indirect=True,
)
def test_unknown_language(marc_record: Record) -> None:
    assert _get_languages(marc_record) == []


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="900716s1991    maub    ob    001 0 lat  "),)],
    indirect=True,
)
def test_known_language(marc_record: Record) -> None:
    language = lone_element(_get_languages(marc_record))
    assert language.id == "lat"
    assert language.label == "Latin"


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="008", data="980407c19909999caumr p o     0   a0mul c"),)],
    indirect=True,
)
def test_multi_language(marc_record: Record) -> None:
    """
    The source data format only supports one language, but there
    is a "language" called "Multiple Languages"
    """
    language = lone_element(_get_languages(marc_record))
    assert language.id == "mul"
    assert language.label == "Multiple languages"
