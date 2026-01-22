from collections.abc import Generator

from models.graph_edge import BaseEdge
from models.graph_node import SourceConcept
from sources.weco_concepts.concepts_source import WeCoConceptsSource
from transformers.base_transformer import BaseTransformer


class WeCoConceptsTransformer(BaseTransformer):
    def __init__(self) -> None:
        super().__init__()
        self.source = WeCoConceptsSource()

    def transform_node(self, data: dict) -> SourceConcept:
        image_url = data.get("image_url")
        return SourceConcept(
            id=data["id"],
            label=data["label"],
            source="weco-authority",
            description=data["description"],
            image_urls=image_url.split("||") if image_url else [],
        )

    def extract_edges(self, data: dict) -> Generator[BaseEdge]:
        """There are no edges to extract from WeCo Concepts."""
        yield from ()
