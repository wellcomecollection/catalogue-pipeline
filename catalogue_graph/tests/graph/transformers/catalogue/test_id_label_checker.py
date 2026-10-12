import unicodedata

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

    # A record with a blank label is still found.
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

    # `get_id` only walks LABEL_MATCH_SOURCES_BY_PRIORITY, so assert on the indexes directly too,
    # to keep the guard which excludes weco labels from them honest.
    assert len(id_label_checker.labels_to_ids["weco-authority"]) == 0
    assert len(id_label_checker.alternative_labels_to_ids["weco-authority"]) == 0
    assert len(id_label_checker.ids_to_labels["weco-authority"]) == 3


def test_id_label_checker_ignores_trailing_stop() -> None:
    id_label_checker = _setup_id_label_checker()

    # LoC label without a stop, catalogue label with one
    assert id_label_checker.get_id("Wesley, John, 1703-1791.", "Person") == "n79060434"
    assert id_label_checker.get_id("Wesley, John, 1703-1791", "Person") == "n79060434"

    # LoC label with a stop, catalogue label without one
    assert id_label_checker.get_id("Fossil tacos", "Concept") == "sh00000076"
    assert id_label_checker.get_id("Fossil tacos.", "Concept") == "sh00000076"

    # Alternative labels differing only by a stop on one record are not ambiguous
    assert id_label_checker.get_id("Taco fossils", "Concept") == "sh00000076"


def test_id_label_checker_matches_decomposed_labels() -> None:
    id_label_checker = _setup_id_label_checker()

    # LoC label precomposed, catalogue label decomposed
    decomposed = unicodedata.normalize("NFD", "Linné, Carl von, 1707-1778")
    assert id_label_checker.get_id(decomposed, "Person") == "n00000034"


def test_id_label_checker_keeps_ellipsis() -> None:
    id_label_checker = _setup_id_label_checker()

    assert id_label_checker.get_id("Tacos and so on...", "Concept") == "sh00000077"
    assert id_label_checker.get_id("Tacos and so on", "Concept") is None
    assert id_label_checker.get_id("Tacos and so on.", "Concept") is None


def test_id_label_checker_keeps_lc_names_aliases_sharing_a_token() -> None:
    id_label_checker = _setup_id_label_checker()

    # An RDA date variant shares the name with the preferred label
    assert (
        id_label_checker.get_id("Gerrish, Samuel, d. 1741", "Person") == "no2008120722"
    )

    # Matching on the preferred label is not subject to the alias check
    assert id_label_checker.get_id("Cook, Stephen S.", "Person") == "n97016028"


def test_id_label_checker_rejects_unrelated_lc_names_aliases() -> None:
    id_label_checker = _setup_id_label_checker()

    # An initialism which is someone else's alias
    assert id_label_checker.get_id("Bliss", "Agent") is None

    # A name which is an alias of a different person
    assert id_label_checker.get_id("Leonardo da Vinci", "Agent") is None
    assert id_label_checker.get_id("LUCIFER", "Person") is None


def test_id_label_checker_rejects_bare_surname_lc_names_aliases() -> None:
    id_label_checker = _setup_id_label_checker()

    # A surname alone shares a token with the preferred label but cannot identify one person
    assert id_label_checker.get_id("Cook", "Agent") is None


def test_id_label_checker_alias_check_is_lc_names_only() -> None:
    id_label_checker = _setup_id_label_checker()

    # LCSH aliases are kept without any token in common with the preferred label
    assert id_label_checker.get_id("Lithographs", "Genre") == "sh85077598"


def test_id_label_checker_alias_check_ignores_dates_and_script() -> None:
    id_label_checker = _setup_id_label_checker()

    # A mononym alias is not a bare surname when the heading is the same name plus dates
    assert id_label_checker.get_id("Avicenna", "Person") == "n00000032"

    # Non-Latin labels keep their tokens rather than folding to nothing
    assert id_label_checker.get_id("Иванов, И., 1900-1980", "Person") == "n00000033"
