import io

from models.graph_node import SourceConcept
from transformers.weco_concepts.concepts_transformer import WeCoConceptsTransformer


def test_stream_weco_nodes() -> None:
    transformer = WeCoConceptsTransformer()
    batches = list(transformer.stream("nodes", 10))
    nodes = batches[0]
    assert len(nodes) == 10
    first_node = nodes[0]
    assert isinstance(first_node, SourceConcept)
    assert first_node.source == "weco-authority"

    # Because the CSV is actually in this repo, we can treat the real data as test data
    # If the data for AIDS changes significantly, or a record is prepended,
    # we'll have to update this test
    assert first_node.id == "weco:zbus63qt"
    assert first_node.label == "Acquired Immunodeficiency Syndrome (AIDS)"
    assert first_node.description is not None
    assert first_node.description.startswith("Thousands of images, texts and films")
    assert first_node.image_urls == [
        "https://iiif.wellcomecollection.org/image/b16692342_l0052826.jp2/info.json",
        "https://iiif.wellcomecollection.org/image/b16763592_L0054224.JP2/info.json",
        "https://iiif.wellcomecollection.org/image/b28669411_0001.jp2/info.json",
        "https://iiif.wellcomecollection.org/image/b16706274_l0052728.jp2/info.json",
    ]


def test_node_without_label_or_description() -> None:
    """a node without image URLs should have an empty list for image_urls."""
    source_data = io.StringIO("""id,label,description,image_url
        aaaaaaaa,,,
        """)
    transformer = WeCoConceptsTransformer(source_data)
    batches = list(transformer.stream("nodes", 1))
    only_node = batches[0][0]
    assert only_node.id == "weco:aaaaaaaa"
    assert only_node.label == ""
    assert only_node.description == ""
    assert only_node.image_urls == []


def test_node_without_images() -> None:
    """a node without image URLs should have an empty list for image_urls."""
    source_data = io.StringIO("""id,label,description,image_url
        aaaaaaaa,Roland le Petour, flatulist to the court of Henry II,
        """)
    transformer = WeCoConceptsTransformer(source_data)
    batches = list(transformer.stream("nodes", 1))
    only_node = batches[0][0]
    assert only_node.id == "weco:aaaaaaaa"
    assert only_node.label == "Roland le Petour"
    assert only_node.description == "flatulist to the court of Henry II"
    assert only_node.image_urls == []


def test_edges() -> None:
    source_data = io.StringIO("""id,label,description,image_url
        aaaaaaaa,Roland le Petour, flatulist to the court of Henry II,
        """)
    transformer = WeCoConceptsTransformer(source_data)
    batches = list(transformer.stream("edges", 1))
    assert len(batches[0]) == 1

    only_edge = batches[0][0]
    assert only_edge.from_id == "aaaaaaaa"
    assert only_edge.to_id == "weco:aaaaaaaa"
    assert only_edge.relationship == "HAS_SOURCE_CONCEPT"
