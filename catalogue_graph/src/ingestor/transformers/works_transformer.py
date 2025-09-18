from datetime import datetime

from ingestor.extractors.works_extractor import ExtractedWork, GraphWorksExtractor
from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.debug.work import SourceWorkDebugInformation, VisibleWorkDebug
from ingestor.models.filter.work import WorkFilterableValues
from ingestor.models.indexable_work import DisplayWork, IndexableWork, QueryWork
from models.events import IncrementalWindow

from .base_transformer import ElasticsearchBaseTransformer
from .work_aggregate_transformer import AggregateWorkTransformer
from .work_display_transformer import DisplayWorkTransformer
from .work_query_transformer import QueryWorkTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(
        self, pipeline_date: str, window: IncrementalWindow | None, is_local: bool
    ) -> None:
        self.source = GraphWorksExtractor(pipeline_date, window, is_local)

    def _transform_display(self, extracted: ExtractedWork) -> DisplayWork:
        work = extracted.work
        transformer = DisplayWorkTransformer(extracted)

        return DisplayWork(
            id=work.state.canonical_id,
            title=work.data.title,
            alternative_titles=work.data.alternative_titles,
            reference_number=work.data.reference_number,
            description=work.data.description,
            physical_description=work.data.physical_description,
            work_type=transformer.work_type,
            lettering=work.data.lettering,
            created_date=transformer.created_date,
            thumbnail=transformer.thumbnail,
            items=list(transformer.items),
            holdings=list(transformer.holdings),
            production=list(transformer.production),
            languages=list(transformer.languages),
            edition=work.data.edition,
            notes=list(transformer.notes),
            duration=work.data.duration,
            current_frequency=work.data.current_frequency,
            former_frequency=work.data.former_frequency,
            designation=work.data.designation,
            images=list(transformer.images),
            identifiers=list(transformer.identifiers),
            contributors=list(transformer.contributors),
            genres=list(transformer.genres),
            subjects=list(transformer.subjects),
            availabilities=transformer.availabilities,
            parts=list(transformer.parts),
            part_of=list(transformer.part_of),
            type=work.data.display_work_type,
        )

    def _transform_query(self, extracted: ExtractedWork) -> QueryWork:
        work = extracted.work
        transformer = QueryWorkTransformer(extracted)

        return QueryWork(
            id=work.state.canonical_id,
            collection_path_label=transformer.collection_path_label,
            collection_path_path=transformer.collection_path,
            alternative_titles=work.data.alternative_titles,
            contributors_agent_label=transformer.contributor_agent_labels,
            genres_concepts_label=list(transformer.genre_concept_labels),
            subjects_concepts_label=list(transformer.subject_concept_labels),
            description=work.data.description,
            edition=work.data.edition,
            source_identifier_value=work.state.source_identifier.value,
            identifiers_value=list(transformer.identifiers),
            images_id=transformer.image_ids,
            images_identifiers_value=list(transformer.image_source_identifiers),
            items_id=list(transformer.item_ids),
            items_identifiers_value=list(transformer.item_identifiers),
            items_shelfmarks_value=list(transformer.item_shelfmarks),
            languages_label=[i.label for i in work.data.languages],
            lettering=work.data.lettering,
            notes_contents=[n.contents for n in work.data.notes],
            production_label=list(transformer.production_labels),
            part_of_title=list(transformer.part_of_titles),
            physical_description=work.data.physical_description,
            reference_number=work.data.reference_number,
            title=work.data.title,
        )

    def _transform_aggregate(self, extracted: ExtractedWork) -> WorkAggregatableValues:
        transformer = AggregateWorkTransformer(extracted)
        return WorkAggregatableValues(
            work_type=list(transformer.work_type),
            genres=list(transformer.genres),
            production_dates=list(transformer.production_dates),
            subjects=list(transformer.subjects),
            languages=list(transformer.languages),
            contributors=list(transformer.contributors),
            item_licenses=list(transformer.licenses),
            availabilities=list(transformer.availabilities),
        )

    def _transform_filter(self, extracted: ExtractedWork) -> WorkFilterableValues:
        transformer = QueryWorkTransformer(extracted)
        work = extracted.work
        return WorkFilterableValues(
            format_id=transformer.format_id,
            work_type=work.data.work_type,
            production_dates_range_from=list(transformer.production_dates_from),
            languages_id=[i.id for i in work.data.languages],
            genres_label=[g.label for g in work.data.genres],
            genres_concepts_id=list(transformer.genre_ids),
            genres_concepts_source_identifier=list(transformer.genre_identifiers),
            subjects_label=[s.normalised_label for s in work.data.subjects],
            subjects_concepts_id=list(transformer.subject_ids),
            subjects_concepts_source_identifier=list(transformer.subject_identifiers),
            contributors_agent_label=list(transformer.contributor_agent_labels),
            contributors_agent_id=list(transformer.contributor_ids),
            contributors_agent_source_identifier=list(
                transformer.contributor_identifiers
            ),
            identifiers_value=list(transformer.identifiers),
            items_locations_license_id=list(transformer.license_ids),
            items_locations_access_conditions_status_id=list(
                transformer.access_condition_status_ids
            ),
            items_id=list(transformer.item_ids),
            items_identifiers_value=list(transformer.item_identifiers),
            items_locations_location_type_id=list(transformer.location_type_ids),
            part_of_id=list(transformer.part_of_ids),
            part_of_title=list(transformer.part_of_titles),
            availabilities_id=[a.id for a in work.state.availabilities],
        )

    def _transform_debug(self, extracted: ExtractedWork) -> VisibleWorkDebug:
        work = extracted.work
        source = SourceWorkDebugInformation(
            id=work.state.canonical_id,
            identifier=work.state.source_identifier,
            version=work.version,
            modified_time=work.state.source_modified_time,
        )

        return VisibleWorkDebug(
            source=source,
            merged_time=work.state.merged_time,
            indexed_time=datetime.now(),
            merge_candidates=work.state.merge_candidates,
            redirect_sources=work.redirect_sources,
        )

    def transform_document(self, extracted: ExtractedWork) -> IndexableWork:
        return IndexableWork(
            query=self._transform_query(extracted),
            display=self._transform_display(extracted),
            aggregatable_values=self._transform_aggregate(extracted),
            filterable_values=self._transform_filter(extracted),
            debug=self._transform_debug(extracted),
            type="Visible",
        )
