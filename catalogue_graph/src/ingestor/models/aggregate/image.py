from pydantic import Field

from ingestor.extractors.images_extractor import ExtractedImage
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkDataTransformer,
)
from models.pipeline.serialisable import ElasticsearchModel


class ImageAggregatableValues(ElasticsearchModel):
    licenses: list[AggregatableField] = Field(serialization_alias="locations.license")
    contributors: list[AggregatableField] = Field(
        serialization_alias="source.contributors.agent"
    )
    genres: list[AggregatableField] = Field(serialization_alias="source.genres")
    subjects: list[AggregatableField] = Field(serialization_alias="source.subjects")

    @classmethod
    def from_extracted_image(
        cls, extracted: ExtractedImage
    ) -> "ImageAggregatableValues":
        transformer = AggregateWorkDataTransformer(
            extracted.image.source.data, extracted.concepts
        )
        return ImageAggregatableValues(
            genres=list(transformer.genres),
            subjects=list(transformer.subjects),
            contributors=list(transformer.contributors),
            licenses=list(transformer.licenses),
        )
