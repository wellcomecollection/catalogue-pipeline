from transformers.catalogue.raw_work import RawCatalogueWork


def _get_test_work(identifier: str, collection_path: str | None) -> dict:
    return {
        "data": {"collectionPath": {"path": collection_path}},
        "state": {"sourceIdentifier": {"value": identifier}},
    }


def test_extracts_parent_when_identifier_equal_to_full_collection_path() -> None:
    test_data = _get_test_work("SADRS/E/1/30", "SADRS/E/1/30")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "SADRS/E/1/30"
    assert raw_work.parent_path_identifier == "SADRS/E/1"

    test_data = _get_test_work("SADRS/E/1", "SADRS/E/1")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "SADRS/E/1"
    assert raw_work.parent_path_identifier == "SADRS/E"

    test_data = _get_test_work("SADRS/E", "SADRS/E")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "SADRS/E"
    assert raw_work.parent_path_identifier == "SADRS"


def test_extracts_parent_when_identifier_equal_to_collection_path_fragment() -> None:
    test_data = _get_test_work("3338943i.27", "3338943i/3338943i.27")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "3338943i.27"
    assert raw_work.parent_path_identifier == "3338943i"

    test_data = _get_test_work("531587i", "3303244i/3288731i/531587i")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "531587i"
    assert raw_work.parent_path_identifier == "3288731i"


def test_extracts_parent_when_identifier_equal_to_collection_path_sub_fragment() -> (
    None
):
    test_data = _get_test_work("653162i", "148769i/fol_1_pl_2_653162i")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "fol_1_pl_2_653162i"
    assert raw_work.parent_path_identifier == "148769i"


def test_does_not_extract_parent_when_no_slashes_in_collection_path() -> None:
    test_data = _get_test_work("SADRS", "SADRS")
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier == "SADRS"
    assert raw_work.parent_path_identifier is None

    test_data = _get_test_work("SADRS", None)
    raw_work = RawCatalogueWork(test_data)
    assert raw_work.path_identifier is None
    assert raw_work.parent_path_identifier is None
