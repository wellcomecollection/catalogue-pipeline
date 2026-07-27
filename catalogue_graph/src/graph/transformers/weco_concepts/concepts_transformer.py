from collections.abc import Generator
from typing import TextIO

from graph.sources.weco_concepts.concepts_source import WeCoConceptsSource
from graph.transformers.catalogue.id_label_checker import WECO_ID_PREFIX
from graph.transformers.graph_transformer import GraphBaseTransformer
from models.graph_edge import BaseEdge
from models.graph_node import SourceConcept


class WeCoConceptsTransformer(GraphBaseTransformer):
    def __init__(self, source_csv: TextIO | None = None) -> None:
        super().__init__()
        self.source = WeCoConceptsSource(source_csv)

    @staticmethod
    def _prefixed_id_of(raw_data: dict) -> str:
        """
        Prefix the id from the source to ensure uniqueness across sources.

        In the source data, the id serves double-duty:

        It is the canonical id for an existing record in the graph,
        which may have come from any other source,
        but it is also the id of this source record in the Wellcome name authority.

        `CatalogueConceptsTransformer` relies on this to work out which catalogue concepts the
        Wellcome name authority holds an override for, so both sides must agree on the prefix.
        """
        return f"{WECO_ID_PREFIX}{raw_data['id'].strip()}"

    def transform_node(self, data: dict) -> SourceConcept:
        image_url = data.get("image_url")
        return SourceConcept(
            id=self._prefixed_id_of(data),
            label=data["label"].strip(),
            source="weco-authority",
            description=data["description"].strip(),
            image_urls=image_url.split("||") if image_url else [],
        )

    def extract_edges(self, raw_data: dict) -> Generator[BaseEdge]:
        raise NotImplementedError(
            "The Wellcome name authority does not produce edges. Its HAS_SOURCE_CONCEPT edges "
            "start at a catalogue Concept node, which only the incremental pipeline creates, so "
            "the monthly pipeline cannot load them. They are produced by "
            "`CatalogueConceptsTransformer` instead. See "
            "https://github.com/wellcomecollection/platform/issues/6457."
        )
