from collections.abc import Generator

from elasticsearch import Elasticsearch

from graph.sources.augmented_images_source import AugmentedImagesSource
from graph.transformers.graph_transformer import GraphBaseTransformer
from models.events import BasePipelineEvent
from models.graph_edge import WorkHasImage
from models.graph_node import Image
from models.pipeline.location import DigitalLocation

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

    def _get_location(self, raw_node: dict) -> DigitalLocation:
        # All images have one or two locations, one of which is always of type 'iiif-image'
        for raw_location in raw_node["locations"]:
            location = DigitalLocation.model_validate(raw_location)
            if location.location_type.id == "iiif-image":
                # All images have exactly one access condition (ViewOnline/Open)
                access = location.access_conditions
                if (
                    len(access) != 1
                    or access[0].method.type != "ViewOnline"
                    or access[0].status is None
                    or access[0].status.type != "Open"
                ):
                    raise ValueError(
                        f"Unexpected access conditions {access} for image {raw_node}"
                    )

                return location

        raise ValueError(f"Image {raw_node} does not have a IIIF image location.")

    def transform_node(self, raw_node: dict) -> Image:
        location = self._get_location(raw_node)
        return Image(
            id=raw_node["state"]["canonicalId"],
            location_type=location.location_type.id,
            location_url=location.url,
        )

    def extract_edges(self, raw_node: dict) -> Generator[WorkHasImage]:
        yield WorkHasImage(
            from_id=raw_node["source"]["id"]["canonicalId"],
            to_id=raw_node["state"]["canonicalId"],
        )
