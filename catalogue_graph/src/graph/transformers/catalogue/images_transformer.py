from collections.abc import Generator

from elasticsearch import Elasticsearch

from graph.sources.augmented_images_source import AugmentedImagesSource
from graph.transformers.graph_transformer import GraphBaseTransformer
from models.events import BasePipelineEvent
from models.graph_edge import WorkHasImage
from models.graph_node import Image

ES_FIELDS = ["state.canonicalId", "source.id", "locations"]


class CatalogueImagesTransformer(GraphBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
    ):
        self.source = AugmentedImagesSource(
            event, fields=ES_FIELDS, es_client=es_client
        )

    def _get_location(self, raw_node: dict) -> dict:
        # All images have one or two locations, one of which is always of type 'iiif-image'
        for location in raw_node["locations"]:
            if location["locationType"]["id"] == "iiif-image":
                # All images have exactly one access condition (ViewOnline/Open)
                # assert location["accessConditions"][0]["method"]["type"] == "ViewOnline", location
                # assert location["accessConditions"][0]["status"]["type"] == "Open", location
                return location

        raise ValueError(f"Image {raw_node} does not have a IIIF image location.")

    def transform_node(self, raw_node: dict) -> Image:
        location = self._get_location(raw_node)
        return Image(
            id=raw_node["state"]["canonicalId"],
            location_type=location["locationType"]["id"],
            location_url=location["url"],
        )

    def extract_edges(self, raw_node: dict) -> Generator[WorkHasImage]:
        yield WorkHasImage(
            from_id=raw_node["source"]["id"]["canonicalId"],
            to_id=raw_node["state"]["canonicalId"],
        )
