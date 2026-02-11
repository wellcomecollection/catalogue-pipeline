from collections.abc import Generator
from typing import TextIO

from models.graph_edge import (
    SourceConceptSameAs,
    SourceConceptSameAsAttributes,
)
from models.graph_node import SourceConcept
from sources.weco_concepts.concepts_source import WeCoConceptsSource
from transformers.base_transformer import BaseTransformer


class WeCoConceptsTransformer(BaseTransformer):
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
        but it is also the id of this source record in the Wellcome name authority,
        """
        return f"weco:{raw_data['id'].strip()}"

    def transform_node(self, data: dict) -> SourceConcept:
        image_url = data.get("image_url")
        return SourceConcept(
            id=self._prefixed_id_of(data),
            label=data["label"].strip(),
            source="weco-authority",
            description=data["description"].strip(),
            image_urls=image_url.split("||") if image_url else [],
        )

    def extract_edges(self, raw_data: dict) -> Generator[SourceConceptSameAs]:
        """
        The Wellcome name authority exists mostly to override names and descriptions
        found in other authorities.
        All records in this set are expected to have a Wellcome ID that refers
        to the record it is intended to override.
        """
        yield SourceConceptSameAs(
            from_id=self._prefixed_id_of(raw_data),  # This record's source id
            to_id=str(
                raw_data["id"].strip()
            ),  # lookup the id elsewhere and find the corresponding source concept
            attributes=SourceConceptSameAsAttributes(source="weco-authority"),
        )
