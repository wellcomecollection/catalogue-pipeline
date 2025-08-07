from ingestor.extractors.works_extractor import ExtractedWork, GraphWorksExtractor
from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.indexable_work import DisplayWork, IndexableWork, QueryWork

from .base_transformer import ElasticsearchBaseTransformer
from .work_aggregate_transformer import AggregateWorkTransformer
from .work_display_transformer import DisplayWorkTransformer
from .work_query_transformer import QueryWorkTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source = GraphWorksExtractor(start_offset, end_index, is_local)

    def _transform_display(self, extracted: ExtractedWork) -> DisplayWork:
        work = extracted.work
        transformer = DisplayWorkTransformer(extracted)

        return DisplayWork(
            id=work.state.canonical_id,
            title=work.data.title,
            alternativeTitles=work.data.alternative_titles,
            referenceNumber=work.data.reference_number,
            description=work.data.description,
            physicalDescription=work.data.physical_description,
            workType=transformer.work_type,
            lettering=work.data.lettering,
            createdDate=transformer.created_date,
            thumbnail=transformer.thumbnail,
            items=list(transformer.items),
            holdings=list(transformer.holdings),
            production=list(transformer.production),
            languages=list(transformer.languages),
            edition=work.data.edition,
            notes=list(transformer.notes),
            duration=work.data.duration,
            currentFrequency=work.data.current_frequency,
            formerFrequency=work.data.former_frequency,
            designation=work.data.designation,
            images=list(transformer.images),
            identifiers=list(transformer.identifiers),
            contributors=list(transformer.contributors),
            genres=list(transformer.genres),
            subjects=list(transformer.subjects),
            availabilities=transformer.availabilities,
            parts=transformer.parts,
            partOf=transformer.part_of,
        )

    def _transform_query(self, extracted: ExtractedWork) -> QueryWork:
        work = extracted.work
        transformer = QueryWorkTransformer(extracted)

        return QueryWork(
            id=work.state.canonical_id,
            collectionPathLabel=transformer.collection_path_label,
            collectionPathPath=transformer.collection_path,
            alternativeTitles=work.data.alternative_titles,
            contributorsAgentLabel=transformer.contributor_labels,
            genresConceptsLabel=list(transformer.genre_labels),
            subjectsConceptsLabel=list(transformer.subject_labels),
            description=work.data.description,
            edition=work.data.edition,
            sourceIdentifierValue=work.state.source_identifier.value,
            identifiersValue=list(transformer.identifiers),
            imagesId=transformer.image_ids,
            imagesIdentifiersValue=list(transformer.image_source_identifiers),
            itemsId=list(transformer.item_ids),
            itemsIdentifiersValue=list(transformer.item_identifiers),
            itemsShelfmarksValue=list(transformer.item_shelfmarks),
            languagesLabel=[i.label for i in work.data.languages],
            lettering=work.data.lettering,
            notesContents=[n.contents for n in work.data.notes],
            productionLabel=list(transformer.production_labels),
            partOfTitle=list(transformer.part_of_titles),
            physicalDescription=work.data.physical_description,
            referenceNumber=work.data.reference_number,
            title=work.data.title,
        )

    def _transform_aggregate(self, extracted: ExtractedWork) -> WorkAggregatableValues:
        transformer = AggregateWorkTransformer(extracted)
        return WorkAggregatableValues(
            workType=list(transformer.work_type),
            genres=list(transformer.genres),
            productionDates=list(transformer.production_dates),
            subjects=list(transformer.subjects),
            languages=list(transformer.languages),
            contributors=list(transformer.contributors),
            itemLicenses=list(transformer.licenses),
            availabilities=list(transformer.availabilities),
        )

    def transform_document(self, extracted: ExtractedWork) -> IndexableWork:
        return IndexableWork(
            query=self._transform_query(extracted),
            display=self._transform_display(extracted),
            aggregatableValues=self._transform_aggregate(extracted),
        )
