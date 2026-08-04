from models.pipeline.archive_type import get_archive_type
from models.pipeline.id_label import IdLabel


def test_get_archive_type_known_prefix() -> None:
    assert get_archive_type("PP/RAS/A.2/1") == IdLabel(id="PP", label="Personal Papers")


def test_get_archive_type_strips_numeric_suffix_for_known_prefixes() -> None:
    assert get_archive_type("OH1/B/3") == IdLabel(id="OH", label="Oral History")


def test_get_archive_type_keeps_numeric_suffix_for_other_prefixes() -> None:
    # GC is not in _NUMERIC_SUFFIX_PREFIXES, so "GC176" is not stripped down to "GC"
    assert get_archive_type("GC176/C/1") is None


def test_get_archive_type_unknown_prefix() -> None:
    assert get_archive_type("XYZ/1") is None
