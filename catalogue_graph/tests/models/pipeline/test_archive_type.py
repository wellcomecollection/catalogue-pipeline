from models.pipeline.archive_type import ArchiveType
from models.pipeline.collection_path import CollectionPath


def get_archive_type(collection_path_label: str) -> ArchiveType | None:
    path = CollectionPath(path=collection_path_label, label=collection_path_label)
    return ArchiveType.from_collection_path(path)


def test_get_archive_type_known_prefix() -> None:
    assert get_archive_type("PP/RAS/A.2/1") == ArchiveType(
        id="PP", label="Personal papers"
    )


def test_get_archive_type_strips_numeric_suffix_for_known_prefixes() -> None:
    assert get_archive_type("OH1/B/3") == ArchiveType(id="OH", label="Oral History")


def test_get_archive_type_keeps_numeric_suffix_for_other_prefixes() -> None:
    # GC is not in _NUMERIC_SUFFIX_PREFIXES, so "GC176" is not stripped down to "GC"
    assert get_archive_type("GC176/C/1") is None


def test_get_archive_type_unknown_prefix() -> None:
    assert get_archive_type("XYZ/1") is None
