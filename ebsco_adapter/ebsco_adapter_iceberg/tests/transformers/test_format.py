import pytest
from pymarc.record import Field, Record, Subfield

from transformers.ebsco_to_weco import transform_record


def test_no_format(marc_record: Record) -> None:
    assert transform_record(marc_record).format is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="006",
                    data="||||||o|||||"
                ),
        )
    ],
    indirect=True,
)
def test_bad_biblevel_is_no_format(marc_record: Record) -> None:
    """
    Bibliographic Level is position 7 in the leader
    We are only interested in m (monograph) and s (serial)
    """
    marc_record.leader = "|||||ax||||"
    assert transform_record(marc_record).format is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="006",
                    data="||||||o|||||"
                ),
        )],
    indirect=True,
)
def test_bad_record_type_is_no_format(marc_record: Record) -> None:
    """
    Type of Record is position 6 in the leader
    We are only interested in a (language material)
    """
    marc_record.leader = "|||||cm|||||"

    assert transform_record(marc_record).format is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="006",
                    data="||||||q|||||"
                ),
        )],
    indirect=True,
)
def test_offline_is_no_format(marc_record: Record) -> None:
    """
    Form of Item is position 6 in control field 006
    We are only interested in o (online)
    """
    marc_record.leader = "|||||am|||||"

    assert transform_record(marc_record).format is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="006",
                    data="m     o  d  ||||||"
                ),
        )],
    indirect=True,
)
def test_ebook(marc_record: Record) -> None:
    """
    An online monograph is an ebook
    """
    marc_record.leader = "00000nam a22000003a 4500"
    format = transform_record(marc_record).format
    assert format.id == "v"
    assert format.label == "E-Books"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="006",
                    data="m     o  d  ||||||"
                ),
        )],
    indirect=True,
)
def test_ejournal(marc_record: Record) -> None:
    """
    An online monograph is an ebook
    """
    marc_record.leader = "00000nas a22000003i 450"
    format = transform_record(marc_record).format
    assert format.id == "j"
    assert format.label == "E-Journals"
