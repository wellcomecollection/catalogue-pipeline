from unittest.mock import MagicMock, patch

from graph.removers.catalogue_concepts_remover import CatalogueConceptsGraphRemover
from models.events import IncrementalGraphRemoverEvent
from models.incremental_window import IncrementalWindow

EXTRACTED_NODES = [{":ID": "concept01"}, {":ID": "concept02"}, {":ID": "concept03"}]

EXTRACTED_EDGES = [
    {
        ":ID": "HAS_SOURCE_CONCEPT:concept01-->sh00000001",
        ":START_ID": "concept01",
        ":END_ID": "sh00000001",
    },
    {
        ":ID": "HAS_SOURCE_CONCEPT:concept02-->D000001",
        ":START_ID": "concept02",
        ":END_ID": "D000001",
    },
]

GRAPH_EDGES = {
    "concept01": {
        "HAS_SOURCE_CONCEPT:concept01-->sh00000001",
        "HAS_SOURCE_CONCEPT:concept01-->n00000001",
    },
    "concept02": {"HAS_SOURCE_CONCEPT:concept02-->D000001"},
    "concept03": {"HAS_SOURCE_CONCEPT:concept03-->n00000002"},
}


def _make_remover(
    window: IncrementalWindow | None = None,
) -> tuple[CatalogueConceptsGraphRemover, MagicMock]:
    event = IncrementalGraphRemoverEvent(
        pipeline_date="2025-01-01",
        graph_date="2025-01-01",
        transformer_type="catalogue_concepts",
        entity_type="edges",
        window=window,
    )
    neptune_client = MagicMock()
    neptune_client.get_node_edges.return_value = GRAPH_EDGES
    return CatalogueConceptsGraphRemover(event, neptune_client), neptune_client


def _fake_csv(s3_uri: str) -> list[dict]:
    assert "/full/" in s3_uri
    if s3_uri.endswith("catalogue_concepts__edges.csv"):
        return EXTRACTED_EDGES
    if s3_uri.endswith("catalogue_concepts__nodes.csv"):
        return EXTRACTED_NODES
    raise AssertionError(f"Unexpected S3 URI {s3_uri}")


def test_get_total_edge_count_counts_concept_source_edges() -> None:
    remover, neptune_client = _make_remover()
    neptune_client.get_total_edge_count.return_value = 7

    assert remover.get_total_edge_count() == 7
    neptune_client.get_total_edge_count.assert_called_once_with(
        "HAS_SOURCE_CONCEPT", source_label="Concept"
    )


def test_windowed_run_removes_no_edges() -> None:
    remover, neptune_client = _make_remover(
        window=IncrementalWindow.model_validate({"end_time": "2025-01-01T12:00"})
    )

    assert list(remover.get_edge_ids_to_remove()) == []
    neptune_client.get_node_edges.assert_not_called()


def test_full_run_removes_edges_absent_from_the_extract() -> None:
    remover, neptune_client = _make_remover()

    with patch(
        "graph.removers.catalogue_concepts_remover.get_csv_from_s3",
        side_effect=_fake_csv,
    ):
        removed = sorted(remover.get_edge_ids_to_remove())

    # concept01 keeps its LCSH edge and loses the stale LC Names one; concept03 matched nothing this time
    assert removed == [
        "HAS_SOURCE_CONCEPT:concept01-->n00000001",
        "HAS_SOURCE_CONCEPT:concept03-->n00000002",
    ]
    neptune_client.get_node_edges.assert_called_once()
    requested_ids, kwargs = (
        neptune_client.get_node_edges.call_args.args,
        neptune_client.get_node_edges.call_args.kwargs,
    )
    assert set(requested_ids[0]) == {"concept01", "concept02", "concept03"}
    assert kwargs == {
        "edge_label": "HAS_SOURCE_CONCEPT",
        "node_label": "Concept",
        "outgoing_only": True,
    }


def test_concepts_only_in_the_edges_file_are_still_checked() -> None:
    remover, neptune_client = _make_remover()
    extra_edge = {
        ":ID": "HAS_SOURCE_CONCEPT:concept04-->D000002",
        ":START_ID": "concept04",
        ":END_ID": "D000002",
    }
    neptune_client.get_node_edges.return_value = {
        "concept04": {
            "HAS_SOURCE_CONCEPT:concept04-->D000002",
            "HAS_SOURCE_CONCEPT:concept04-->D000003",
        }
    }

    def fake_csv(s3_uri: str) -> list[dict]:
        if s3_uri.endswith("catalogue_concepts__edges.csv"):
            return [extra_edge]
        return []

    with patch(
        "graph.removers.catalogue_concepts_remover.get_csv_from_s3",
        side_effect=fake_csv,
    ):
        removed = list(remover.get_edge_ids_to_remove())

    assert removed == ["HAS_SOURCE_CONCEPT:concept04-->D000003"]
