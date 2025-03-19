from test_utils import add_mock_transformer_outputs
from transformers.catalogue.id_label_checker import IdLabelChecker


def test_id_label_matcher() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations", "names"]
    )
    id_label_checker = IdLabelChecker(
        node_types=["concepts", "locations", "names"], sources=["loc", "mesh"]
    )

    # Do not match blacklisted concept labels
    assert id_label_checker.get_id("consumption", "Concept") is None
    assert id_label_checker.get_id("consumption", "Person") is None

    # Do not use alternative labels to match things to people
    assert id_label_checker.get_id("macquerry, maureen, 1955-", "Concept") is None
    assert id_label_checker.get_id("macquerry, maureen, 1955-", "Person") == "n00000001"
