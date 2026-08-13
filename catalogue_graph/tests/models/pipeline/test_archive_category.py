from models.pipeline.archive_category import ArchiveCategory
from models.pipeline.collection_path import CollectionPath


def get_archive_category(collection_path_label: str) -> ArchiveCategory | None:
    path = CollectionPath(path=collection_path_label, label=collection_path_label)
    return ArchiveCategory.from_collection_path(path)


def test_get_archive_category_known_prefix() -> None:
    assert get_archive_category("PP/RAS/A.2/1") == ArchiveCategory(
        id="PP", label="Personal papers"
    )


def test_get_archive_category_strips_numeric_suffix_for_known_prefixes() -> None:
    assert get_archive_category("OH1/B/3") == ArchiveCategory(
        id="OH", label="Oral History"
    )


def test_get_archive_category_keeps_numeric_suffix_for_other_prefixes() -> None:
    # GC is not in _NUMERIC_SUFFIX_PREFIXES, so "GC176" is not stripped down to "GC"
    assert get_archive_category("GC176/C/1") is None


def test_get_archive_category_unknown_prefix() -> None:
    assert get_archive_category("XYZ/1") is None
