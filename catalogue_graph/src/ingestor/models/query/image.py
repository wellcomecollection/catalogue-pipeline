from ingestor.extractors.images_extractor import ExtractedImage
from models.pipeline.serialisable import ElasticsearchModel

from .work import QueryWork


class QueryImage(ElasticsearchModel):
    id: str
    source: QueryWork

    @classmethod
    def from_extracted_image(cls, extracted: ExtractedImage) -> "QueryImage":
        source_work = extracted.image.source
        return QueryImage(
            id=extracted.image.state.canonical_id,
            source=QueryWork.from_work_data(
                data=source_work.data,
                concepts=extracted.concepts,
                work_id=source_work.id.canonical_id,
                source_identifier=source_work.id.source_identifier,
            ),
        )
