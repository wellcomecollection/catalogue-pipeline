from transformers.base_transformer import BaseTransformer
from sources.weco_concepts.concepts_source import WeCoConceptsSource
from models.graph_node import SourceConcept


class WeCoConceptsTransformer(BaseTransformer):
    def __init__(self):
        self.source = WeCoConceptsSource()

    def transform_node(self, data) -> SourceConcept:
        return SourceConcept(
            id=data["id"],
            label=data["label"],
            source="weco-authority",
            description=data["description"],
            image_urls=data.get("image_url", "").split("||"),
        )

    def extract_edges(self, data):
        """There are no edges to extract from WeCo Concepts."""
        yield from ()
