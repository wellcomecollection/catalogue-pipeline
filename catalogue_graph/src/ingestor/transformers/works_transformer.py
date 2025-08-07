from ingestor.extractors.works_extractor import GraphWorksExtractor
from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.indexable_work import DisplayWork, IndexableWork, QueryWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from ingestor.transformers.raw_work import RawNeptuneWork

from .base_transformer import ElasticsearchBaseTransformer
from .work_display_transformer import DisplayWorkTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source = GraphWorksExtractor(start_offset, end_index, is_local)        
    
    def transform_document(
        self, raw_data: tuple[DenormalisedWork, WorkHierarchy, list[WorkConcept]]
    ) -> IndexableWork:
        denormalised_work, hierarchy_data, concepts_data = raw_data
        raw_work = RawNeptuneWork(denormalised_work, hierarchy_data, concepts_data)

        data = denormalised_work.data
        state = denormalised_work.state
        
        transformer = DisplayWorkTransformer(raw_data[0], raw_data[1], raw_data[2])
        display = DisplayWork(
            id=state.canonicalId,
            title=data.title,
            alternativeTitles=data.alternativeTitles,
            referenceNumber=data.referenceNumber,
            description=data.description,
            physicalDescription=data.physicalDescription,
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
            currentFrequency=data.currentFrequency,
            formerFrequency=data.formerFrequency,
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

        query = QueryWork(
            id=state.canonicalId,
            collectionPathLabel=data.collectionPath.label
            if data.collectionPath
            else None,
            collectionPathPath=data.collectionPath.path
            if data.collectionPath
            else None,
            alternativeTitles=data.alternativeTitles,
            contributorsAgentLabel=[c.agent.label for c in data.contributors],
            genresConceptsLabel=raw_work.genre_labels,
            subjectsConceptsLabel=raw_work.subject_labels,
            description=data.description,
            edition=data.edition,
            sourceIdentifierValue=state.sourceIdentifier.value,
            identifiersValue=raw_work.other_identifiers,
            imagesId=raw_work.image_ids,
            imagesIdentifiersValue=raw_work.image_source_identifiers,
            itemsId=raw_work.item_ids,
            itemsIdentifiersValue=raw_work.item_identifiers,
            itemsShelfmarksValue=raw_work.item_shelfmarks,
            languagesLabel=[i.label for i in data.languages],
            lettering=data.lettering,
            notesContents=[n.contents for n in data.notes],
            productionLabel=raw_work.production_labels,
            partOfTitle=raw_work.part_of_titles,
            physicalDescription=data.physicalDescription,
            referenceNumber=data.referenceNumber,
            title=data.title,
        )

        return IndexableWork(query=query, display=display)
