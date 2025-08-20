from test_utils import add_mock_transformer_outputs

from transformers.catalogue.id_label_checker import IdLabelChecker
from utils.aws import get_transformers_from_ontology
from utils.types import OntologyType


def _setup_id_label_checker() -> IdLabelChecker:
    ontologies: list[OntologyType] = ["loc", "mesh"]
    pipeline_date = "2025-01-01"

    add_mock_transformer_outputs(ontologies, pipeline_date)
    transformers = []
    for ontology in ontologies:
        transformers += get_transformers_from_ontology(ontology)
    return IdLabelChecker(transformers, pipeline_date)


def test_id_label_checker_label_matching() -> None:
    id_label_checker = _setup_id_label_checker()

    # Match on label
    assert id_label_checker.get_id("tacos", "Concept") == "sh00000002"

    # Match on uppercase label
    assert id_label_checker.get_id("TACOS", "Concept") == "sh00000002"

    # Match on alternative label
    assert id_label_checker.get_id("etching_s", "Concept") == "sh85045046"
    assert id_label_checker.get_id("Some example concept", "Concept") == "sh85123237"


def test_id_label_checker_denylist() -> None:
    id_label_checker = _setup_id_label_checker()

    # Do not match denylisted concept labels
    assert id_label_checker.get_id("consumption", "Concept") is None
    assert id_label_checker.get_id("consumption", "Person") is None


def test_id_label_checker_things_to_people() -> None:
    id_label_checker = _setup_id_label_checker()

    # Do not use alternative labels to match things to people
    assert id_label_checker.get_id("macquerry, maureen, 1955-", "Concept") is None
    assert id_label_checker.get_id("macquerry, maureen, 1955-", "Person") == "n00000001"

    # But we are not as strict when it comes to main labels
    assert id_label_checker.get_id("mcquerry, maureen, 1955-", "Concept") == "n00000001"


def test_id_label_checker_people_to_things() -> None:
    id_label_checker = _setup_id_label_checker()

    # Do not use alternative labels to match people to things
    assert id_label_checker.get_id("consumer price index", "Person") is None
    assert id_label_checker.get_id("consumer price index", "Concept") == "D004467"

    # But we are not as strict when it comes to main labels
    assert id_label_checker.get_id("anatomy", "Person") == "D000715"


def test_id_label_checker_label_priority() -> None:
    id_label_checker = _setup_id_label_checker()

    # Prioritise matching on main label rather than alternative label
    assert id_label_checker.get_id("Example concept", "Genre") == "sh85004839"
    assert id_label_checker.get_id("Another example concept", "Genre") == "sh85123237"


def test_id_label_checker_source_priority() -> None:
    id_label_checker = _setup_id_label_checker()

    # Prioritise matching on MeSH rather than LoC
    assert id_label_checker.get_id("anatomy", "Concept") == "D000715"
