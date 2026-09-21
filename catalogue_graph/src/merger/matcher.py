"""Connected components over merge-candidate edges: the matcher as a function."""

import numpy as np
import polars as pl
import structlog
from pyiceberg.table import Table as IcebergTable

logger = structlog.get_logger(__name__)

GRAPH_COLUMNS = ("id", "last_modified", "type", "merge_candidate_ids")


def load_works(table: IcebergTable, snapshot_id: int) -> pl.DataFrame:
    """The most recently written row of every identified work, graph columns only."""
    scan = table.scan(selected_fields=GRAPH_COLUMNS, snapshot_id=snapshot_id)
    works = pl.DataFrame(scan.to_arrow())
    return works.sort("last_modified", descending=True).unique("id", keep="first")


def match_works(works: pl.DataFrame) -> pl.DataFrame:
    """Label every work with its component id: the smallest id among the works it is linked to."""
    # One row per edge, from a work to each of its merge candidates.
    edges = works.select("id", "merge_candidate_ids").explode("merge_candidate_ids")
    edges = edges.drop_nulls().rename({"id": "src", "merge_candidate_ids": "dst"})
    edges = edges.filter(pl.col("src") != pl.col("dst"))

    # Nothing matches through a Deleted work, so drop the edges into and out of them.
    deleted = works.filter(pl.col("type") == "Deleted").select("id")
    edges = edges.join(deleted, left_on="src", right_on="id", how="anti")
    edges = edges.join(deleted, left_on="dst", right_on="id", how="anti")

    # Every work id, plus placeholder ids for candidates that have not been seen yet.
    # Two works pointing at the same missing work are still matched together.
    nodes = pl.concat(
        [works.select("id"), edges.select(pl.col("dst").alias("id"))]
    ).unique()
    # Sorted before numbering, so the smallest index in a component is also its smallest id.
    nodes = nodes.sort("id").with_row_index("index")

    # Replace the string ids on each edge with the integer indices of its two ends.
    indexed_edges = edges.join(nodes, left_on="src", right_on="id")
    indexed_edges = indexed_edges.join(
        nodes, left_on="dst", right_on="id", suffix="_dst"
    )
    src = indexed_edges["index"].to_numpy().astype(np.int64)
    dst = indexed_edges["index_dst"].to_numpy().astype(np.int64)

    # Each node ends up labelled with the smallest index in its component; map that back to an id.
    labels = _connected_components(nodes.height, src, dst)
    components = nodes.select("id", component_id=nodes["id"].gather(labels))

    logger.info(
        "Matched works",
        works=works.height,
        placeholders=nodes.height - works.height,
        edges=edges.height,
        components=components["component_id"].n_unique(),
    )
    # Placeholders drop out here: only real works are labelled and passed on.
    return works.select("id", "last_modified", "type").join(
        components, on="id", how="left"
    )


def _connected_components(
    node_count: int, src: np.ndarray, dst: np.ndarray
) -> np.ndarray:
    """Label propagation: each node takes the lowest label in its neighbourhood until stable."""
    labels = np.arange(node_count)
    while True:
        lowest = np.minimum(labels[src], labels[dst])
        updated = labels.copy()
        np.minimum.at(updated, src, lowest)
        np.minimum.at(updated, dst, lowest)
        updated = updated[updated]
        if np.array_equal(updated, labels):
            return labels
        labels = updated
