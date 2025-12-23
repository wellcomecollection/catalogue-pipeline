import pytest
from pymarc.record import Field, Record

from models.pipeline.id_label import Format

from .ebsco_test_transformer import transform_ebsco_record


def _transform_format(marc_record: Record) -> Format | None:
    return transform_ebsco_record(marc_record).data.format


def test_no_format(marc_record: Record) -> None:
    assert _transform_format(marc_record) is None


def test_no_006_no_format(marc_record: Record) -> None:
    """
    Can't work out the format if there's no 006
    """
    marc_record.leader = "|||||am||||"
    assert _transform_format(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="006", data="||||||o|||||"),)],
    indirect=True,
)
def test_bad_biblevel_is_no_format(marc_record: Record) -> None:
    """
    Bibliographic Level is position 7 in the leader
    We are only interested in m (monograph) and s (serial)
    """
    marc_record.leader = "|||||ax||||"
    assert _transform_format(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="006", data="||||||o|||||"),)],
    indirect=True,
)
def test_bad_record_type_is_no_format(marc_record: Record) -> None:
    """
    Type of Record is position 6 in the leader
    We are only interested in a (language material)
    """
    marc_record.leader = "|||||cm|||||"

    assert _transform_format(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="006", data="||||||q|||||"),)],
    indirect=True,
)
def test_offline_is_no_format(marc_record: Record) -> None:
    """
    Form of Item is position 6 in control field 006
    We are only interested in o (online)
    """
    marc_record.leader = "|||||am|||||"

    assert _transform_format(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="006", data="m     o  d  ||||||"),)],
    indirect=True,
)
def test_ebook(marc_record: Record) -> None:
    """
    An online monograph is an ebook
    """
    marc_record.leader = "00000nam a22000003a 4500"
    format = _transform_format(marc_record)
    assert format is not None
    assert format.id == "v"
    assert format.label == "E-books"


@pytest.mark.parametrize(
    "marc_record",
    [(Field(tag="006", data="m     o  d  ||||||"),)],
    indirect=True,
)
def test_ejournal(marc_record: Record) -> None:
    """
    An online monograph is an ebook
    """
    marc_record.leader = "00000nas a22000003i 450"
    format = _transform_format(marc_record)
    assert format is not None
    assert format.id == "j"
    assert format.label == "E-journals"
