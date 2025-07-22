
from sources.catalogue.work_identifiers_source import RawDenormalisedWorkIdentifier
from transformers.catalogue.raw_work_identifier import RawCatalogueWorkIdentifier


def _get_test_identifier(identifier: str, collection_path: str | None) -> RawDenormalisedWorkIdentifier:
    return RawDenormalisedWorkIdentifier(
        identifier={'identifierType': {'id': 'some-type'}, 'ontologyType': 'Work', 'value': identifier},
        referenced_in="otherIdentifiers",
        work_id="123",
        work_collection_path=collection_path
    )


def test_extracts_parent_when_identifier_equal_to_full_collection_path() -> None:
    test_data = _get_test_identifier("SADRS/E/1/30", "SADRS/E/1/30")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent == "some-type||SADRS/E/1"

    test_data = _get_test_identifier("SADRS/E/1", "SADRS/E/1")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent == "some-type||SADRS/E"

    test_data = _get_test_identifier("SADRS/E", "SADRS/E")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent == "some-type||SADRS"


def test_extracts_parent_when_identifier_equal_to_collection_path_fragment() -> None:
    test_data = _get_test_identifier("3338943i.27", "3338943i/3338943i.27")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent == "some-type||3338943i"

    test_data = _get_test_identifier("531587i", "3303244i/3288731i/531587i")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent == "some-type||3288731i"


def test_extracts_parent_when_identifier_equal_to_collection_path_sub_fragment() -> None:
    test_data = _get_test_identifier("653162i", "148769i/fol_1_pl_2_653162i")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent == "some-type||148769i"


def test_does_not_extract_parent_when_no_slashes_in_collection_path() -> None:
    test_data = _get_test_identifier("SADRS", "SADRS")
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent is None

    test_data = _get_test_identifier("SADRS", None)
    raw_identifier = RawCatalogueWorkIdentifier(test_data)
    assert raw_identifier.parent is None
