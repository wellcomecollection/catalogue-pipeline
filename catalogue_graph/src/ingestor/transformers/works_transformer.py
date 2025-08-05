from ingestor.extractors.works_extractor import GraphWorksExtractor
from ingestor.models.indexable_work import IndexableWork, WorkDisplay, WorkQuery
from ingestor.transformers.raw_work import RawNeptuneWork

from .base_transformer import ElasticsearchBaseTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source = GraphWorksExtractor(start_offset, end_index, is_local)

    def transform_document(self, raw_data: tuple) -> IndexableWork:
        work_data, hierarchy_data, concepts_data = raw_data
        raw_work = RawNeptuneWork(work_data, hierarchy_data, concepts_data)

        # query = WorkQuery(
        #     id=raw_work.wellcome_id,
        #     title=raw_work.title,
        #     alternativeTitles=raw_work.alternative_titles,
        #     description=raw_work.description,
        #     edition=raw_work.edition,
        #     lettering=raw_work.lettering,
        #     physicalDescription=raw_work.physical_description,
        #     referenceNumber=raw_work.reference_number
        # )

        display = WorkDisplay(
            id=raw_work.wellcome_id,
            title=raw_work.title,
            alternativeTitles=raw_work.alternative_titles,
            referenceNumber=raw_work.reference_number,
            description=raw_work.description,
            physicalDescription=raw_work.physical_description,
            workType=raw_work.work_type,
            lettering=raw_work.lettering,
            createdDate=raw_work.created_date,
            thumbnail=raw_work.thumbnail,
            items=raw_work.display_items,
            holdings=raw_work.holdings,
            production=raw_work.display_production,
            languages=raw_work.display_languages,
            edition=raw_work.edition,
            notes=raw_work.display_notes,
            duration=raw_work.duration,
            currentFrequency=raw_work.current_frequency,
            formerFrequency=raw_work.former_frequency,
            designation=raw_work.designation,
            images=raw_work.images,
            identifiers=raw_work.display_identifiers,
            contributors=raw_work.display_contributors,
            genres=raw_work.display_genres,
            subjects=raw_work.display_subjects,
            availabilities=raw_work.availabilities,
            parts=raw_work.parts,
            partOf=raw_work.display_part_of,
        )

        query = WorkQuery(
            id=raw_work.wellcome_id,
            collectionPathLabel=raw_work.collection_path_label,
            collectionPathPath=raw_work.collection_path,
            alternativeTitles=raw_work.alternative_titles,
            # contributorsAgentLabel,
            # genresConceptsLabel
            # subjectsConceptsLabel
            description=raw_work.description,
            edition=raw_work.edition,
            sourceIdentifierValue=raw_work.source_identifier,
            identifiersValue=raw_work.other_identifiers,
            imagesId=raw_work.image_ids,
            imagesIdentifiersValue=raw_work.image_source_identifiers,
            itemsId=raw_work.item_ids,
            itemsIdentifiersValue=raw_work.item_identifiers,
            itemsShelfmarksValue=raw_work.item_shelfmarks,
            languagesLabel=raw_work.languages,
            lettering=raw_work.lettering,
            notesContents=raw_work.notes,
            productionLabel=raw_work.production_labels,
            partOfTitle=raw_work.part_of_titles,
            physicalDescription=raw_work.physical_description,
            referenceNumber=raw_work.reference_number,
            title=raw_work.title,
        )

        return display
