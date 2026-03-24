from pydantic import computed_field

from ingestor.extractors.images_extractor import ExtractedImage
from ingestor.transformers.work_display_transformer import DisplayWorkDataTransformer
from models.pipeline.serialisable import ElasticsearchModel

from .concept import DisplayContributor, DisplayGenre, DisplaySubject
from .id_label import DisplayIdLabel
from .location import DisplayDigitalLocation


class DisplayImageSource(ElasticsearchModel):
    id: str
    title: str | None
    contributors: list[DisplayContributor]
    subjects: list[DisplaySubject]
    genres: list[DisplayGenre]
    languages: list[DisplayIdLabel]
    type: str = "oork"

    @classmethod
    def from_extracted_image(cls, extracted: ExtractedImage) -> "DisplayImageSource":
        image = extracted.image
        work_data = image.source.data
        transformer = DisplayWorkDataTransformer(work_data, extracted.concepts)

        return DisplayImageSource(
            id=image.source.id.canonical_id,
            title=work_data.title,
            contributors=list(transformer.contributors),
            genres=list(transformer.genres),
            subjects=list(transformer.subjects),
            languages=list(transformer.languages),
        )


class DisplayImage(ElasticsearchModel):
    id: str
    locations: list[DisplayDigitalLocation]
    aspect_ratio: float
    average_color: str
    source: DisplayImageSource
    type: str = "Image"

    @computed_field
    @property
    def thumbnail(self) -> DisplayDigitalLocation:
        for location in self.locations:
            if location.locationType.id == "iiif-image":
                return location

        raise ValueError(f"No iiif-image (thumbnail) location found on image {self.id}")

    @classmethod
    def from_extracted_image(cls, extracted: ExtractedImage) -> "DisplayImage":
        image = extracted.image
        inferred_data = image.state.inferred_data

        return DisplayImage(
            id=image.source.id.canonical_id,
            locations=[
                DisplayDigitalLocation.from_location(loc) for loc in image.locations
            ],
            aspect_ratio=inferred_data.aspect_ratio or 1,
            average_color=inferred_data.average_color_hex or "#ffffff",
            source=DisplayImageSource.from_extracted_image(extracted),
        )
