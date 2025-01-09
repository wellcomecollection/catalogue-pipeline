from models.graph_node import SourceConcept
from sources.wikidata.concepts_source import WikidataConceptsSource
from transformers.base_transformer import BaseTransformer

from .raw_concept import RawWikidataConcept


class WikidataConceptsTransformer(BaseTransformer):
    def __init__(self):
        self.source = WikidataConceptsSource("concepts", "loc")

    def transform_node(self, raw_node: dict) -> SourceConcept | None:
        raw_concept = RawWikidataConcept(raw_node)

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            description=raw_concept.description,
        )
