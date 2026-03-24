from datetime import datetime

from ingestor.extractors.images_extractor import ExtractedImage
from ingestor.models.aggregate.image import ImageAggregatableValues
from ingestor.models.debug.image import ImageDebug
from ingestor.models.display.image import DisplayImage
from ingestor.models.filter.image import ImageFilterableValues
from ingestor.models.indexable import IndexableRecord
from ingestor.models.query.image import QueryImage
from ingestor.models.vector.image import ImageVectorValues
from utils.timezone import convert_datetime_to_utc_iso


class IndexableImage(IndexableRecord):
    display: DisplayImage
    query: QueryImage
    aggregatable_values: ImageAggregatableValues
    filterable_values: ImageFilterableValues
    vector_values: ImageVectorValues
    debug: ImageDebug
    modified_time: str

    def get_id(self) -> str:
        return self.query.id

    @staticmethod
    def from_raw_document(image: dict) -> "IndexableImage":
        return IndexableImage.model_validate(image)

    @classmethod
    def from_extracted_image(cls, extracted: ExtractedImage) -> "IndexableImage":
        return IndexableImage(
            query=QueryImage.from_extracted_image(extracted),
            display=DisplayImage.from_extracted_image(extracted),
            aggregatable_values=ImageAggregatableValues.from_extracted_image(extracted),
            filterable_values=ImageFilterableValues.from_extracted_image(extracted),
            vector_values=ImageVectorValues.from_augmented_image(extracted.image),
            debug=ImageDebug(indexed_time=convert_datetime_to_utc_iso(datetime.now())),
            modified_time=extracted.image.modified_time,
        )
