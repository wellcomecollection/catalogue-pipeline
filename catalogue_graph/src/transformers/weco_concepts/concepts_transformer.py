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
            source="weco_concepts",
            description=data["description"]
            # TODO also images - once model supports it

        )
