from ingestor.extractors.works_extractor import GraphWorksExtractor
from ingestor.models.indexable_work import IndexableWork, WorkDisplay
from ingestor.transformers.raw_work import RawNeptuneWork

from .base_transformer import ElasticsearchBaseTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source = GraphWorksExtractor(start_offset, end_index, is_local)

    def transform_document(self, raw_data: tuple) -> IndexableWork:
        work_data, hierarchy_data, concepts_data = raw_data
        neptune_work = RawNeptuneWork(work_data, hierarchy_data, concepts_data)

        display = WorkDisplay(
            id=neptune_work.wellcome_id,
            title=neptune_work.title,
            alternativeTitles=neptune_work.alternative_titles,
            referenceNumber=neptune_work.reference_number,
            description=neptune_work.description,
            physicalDescription=neptune_work.physical_description,
            workType=neptune_work.work_type,
            lettering=neptune_work.lettering,
            createdDate=neptune_work.created_date,
            thumbnail=neptune_work.thumbnail,
            items=neptune_work.items,
            holdings=neptune_work.holdings,
            production=neptune_work.production,
            languages=neptune_work.languages,
            edition=neptune_work.edition,
            notes=neptune_work.notes,
            duration=neptune_work.duration,
            currentFrequency=neptune_work.current_frequency,
            formerFrequency=neptune_work.former_frequency,
            designation=neptune_work.designation,
            images=neptune_work.images,
            contributors=neptune_work.contributors,
            identifiers=neptune_work.identifiers,
            subjects=neptune_work.subjects,
            genres=neptune_work.genres,
            availabilities=[],
            parts=neptune_work.parts,
            partOf=neptune_work.part_of
        )
        
        return display
