from ingestor.extractors.images.images_extractor import ExtractedImage
from models.pipeline.serialisable import ElasticsearchModel

from .work import QueryWork


class QueryImage(ElasticsearchModel):
    id: str
    source: QueryWork

    @classmethod
    def from_extracted_image(cls, extracted: ExtractedImage) -> "QueryImage":
        return QueryImage(
            id=extracted.image.state.canonical_id,
            source=QueryWork.from_extracted_work(extracted.work),
        )
