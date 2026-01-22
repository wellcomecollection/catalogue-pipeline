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
    assert first_node.id == "zbus63qt"
    assert first_node.label == "Acquired Immunodeficiency Syndrome (AIDS)"
    assert first_node.description is not None
    assert first_node.description.startswith("Thousands of images, texts and films")
    assert first_node.image_urls == [
        "https://iiif.wellcomecollection.org/image/b16692342_l0052826.jp2/info.json",
        "https://iiif.wellcomecollection.org/image/b16763592_L0054224.JP2/info.json",
        "https://iiif.wellcomecollection.org/image/b28669411_0001.jp2/info.json",
        "https://iiif.wellcomecollection.org/image/b16706274_l0052728.jp2/info.json",
    ]
