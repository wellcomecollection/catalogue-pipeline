from graph.transformers.catalogue.id_label_checker import IdLabelChecker
from models.events import BasePipelineEvent
from tests.test_utils import add_mock_transformer_outputs_for_ontologies
from utils.ontology import get_transformers_from_ontology
from utils.types import OntologyType


def _setup_id_label_checker() -> IdLabelChecker:
    ontologies: list[OntologyType] = ["loc", "mesh", "weco"]
    pipeline_date = "2025-01-01"
    graph_date = "2026-02-02"

    source_event = BasePipelineEvent(pipeline_date=pipeline_date, graph_date=graph_date)

    add_mock_transformer_outputs_for_ontologies(ontologies, pipeline_date, graph_date)
    transformers = []
    for ontology in ontologies:
        transformers += get_transformers_from_ontology(ontology)
    return IdLabelChecker(transformers, source_event)


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


def test_id_label_checker_has_id() -> None:
    id_label_checker = _setup_id_label_checker()

    # A record with a blank label is still found. (Most Wellcome name authority records have one.)
    assert id_label_checker.has_id("weco:s6s24vd7", "weco-authority")
    assert id_label_checker.has_id("sh00000002", "lc-subjects")

    assert not id_label_checker.has_id("weco:notarealid", "weco-authority")

    # Wellcome name authority ids are prefixed, so the bare canonical id is not one of them.
    assert not id_label_checker.has_id("s6s24vd7", "weco-authority")


def test_id_label_checker_never_matches_weco_by_label() -> None:
    id_label_checker = _setup_id_label_checker()

    # 'Example concept' is the label of both an LoC concept and a Wellcome name authority record.
    # The Wellcome name authority is matched by identifier only, so LoC must still win.
    assert id_label_checker.get_id("Example concept", "Concept") == "sh85004839"

    # Blank Wellcome name authority labels must not turn the empty label into a match.
    assert id_label_checker.get_id("", "Concept") is None

    # The assertions above only fail if weco-authority is added back to
    # LABEL_MATCH_SOURCES_BY_PRIORITY, because that is the only list `get_id` walks. Assert on the
    # indexes directly too, so that the guard which keeps weco labels out of them stays honest.
    assert len(id_label_checker.labels_to_ids["weco-authority"]) == 0
    assert len(id_label_checker.alternative_labels_to_ids["weco-authority"]) == 0
    assert len(id_label_checker.ids_to_labels["weco-authority"]) == 3
