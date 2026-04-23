from pydantic import Field

from ingestor.extractors.images.images_extractor import ExtractedImage
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkTransformer,
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
        transformer = AggregateWorkTransformer(extracted.work)
        return ImageAggregatableValues(
            genres=list(transformer.genres),
            subjects=list(transformer.subjects),
            contributors=list(transformer.contributors),
            licenses=list(transformer.licenses),
        )
