from transformers.weco_concepts.concepts_transformer import WeCoConceptsTransformer
from models.graph_node import SourceConcept


def test_stream_weco_nodes() -> None:
    transformer = WeCoConceptsTransformer()
    batches = list(transformer.stream("nodes", 10))
    nodes = batches[0]
    assert len(nodes) == 10
    first_node = nodes[0]
    assert isinstance(first_node, SourceConcept)
    assert first_node.source == "weco_concepts"
    assert first_node.id == "zbus63qt"
    assert first_node.label == "Acquired Immunodeficiency Syndrome (AIDS)"
    assert first_node.description.startswith("Thousands of images, texts and films")
    # TODO also images
