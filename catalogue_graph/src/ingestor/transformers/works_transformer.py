from ingestor.extractors.works_extractor import GraphWorksExtractor
from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.indexable_work import DisplayWork, IndexableWork, QueryWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy

from .base_transformer import ElasticsearchBaseTransformer
from .work_display_transformer import DisplayWorkTransformer
from .work_query_transformer import QueryWorkTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source = GraphWorksExtractor(start_offset, end_index, is_local)

    def transform_document(
        self, raw_data: tuple[DenormalisedWork, WorkHierarchy, list[WorkConcept]]
    ) -> IndexableWork:
        data = raw_data[0].data
        state = raw_data[0].state

        transformer = DisplayWorkTransformer(raw_data[0], raw_data[1], raw_data[2])

        display = DisplayWork(
            id=state.canonical_id,
            title=data.title,
            alternativeTitles=data.alternative_titles,
            referenceNumber=data.reference_number,
            description=data.description,
            physicalDescription=data.physical_description,
            workType=transformer.work_type,
            lettering=data.lettering,
            createdDate=transformer.created_date,
            thumbnail=transformer.thumbnail,
            items=transformer.items,
            holdings=transformer.holdings,
            production=transformer.production,
            languages=transformer.languages,
            edition=data.edition,
            notes=transformer.notes,
            duration=data.duration,
            currentFrequency=data.current_frequency,
            formerFrequency=data.former_frequency,
            designation=data.designation,
            images=transformer.images,
            identifiers=transformer.identifiers,
            contributors=transformer.contributors,
            genres=transformer.genres,
            subjects=transformer.subjects,
            availabilities=transformer.availabilities,
            parts=transformer.parts,
            partOf=transformer.part_of,
        )

        transformer = QueryWorkTransformer(raw_data[0], raw_data[1], raw_data[2])
        query = QueryWork(
            id=state.canonical_id,
            collectionPathLabel=transformer.collection_path_label,
            collectionPathPath=transformer.collection_path,
            alternativeTitles=data.alternative_titles,
            contributorsAgentLabel=transformer.contributor_labels,
            genresConceptsLabel=transformer.genre_labels,
            subjectsConceptsLabel=transformer.subject_labels,
            description=data.description,
            edition=data.edition,
            sourceIdentifierValue=state.source_identifier.value,
            identifiersValue=transformer.identifiers,
            imagesId=transformer.image_ids,
            imagesIdentifiersValue=transformer.image_source_identifiers,
            itemsId=transformer.item_ids,
            itemsIdentifiersValue=transformer.item_identifiers,
            itemsShelfmarksValue=transformer.item_shelfmarks,
            languagesLabel=[i.label for i in data.languages],
            lettering=data.lettering,
            notesContents=[n.contents for n in data.notes],
            productionLabel=transformer.production_labels,
            partOfTitle=transformer.part_of_titles,
            physicalDescription=data.physical_description,
            referenceNumber=data.reference_number,
            title=data.title,
        )

        return IndexableWork(query=query, display=display)
