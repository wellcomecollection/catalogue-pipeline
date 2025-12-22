from datetime import datetime

import dateutil.parser
from pymarc.record import Record

from adapters.ebsco.transformers.contributors import extract_contributors
from adapters.ebsco.transformers.current_frequency import extract_current_frequency
from adapters.ebsco.transformers.description import extract_description
from adapters.ebsco.transformers.designation import extract_designation
from adapters.ebsco.transformers.edition import extract_edition
from adapters.ebsco.transformers.format import extract_format
from adapters.ebsco.transformers.genres import extract_genres
from adapters.ebsco.transformers.holdings import extract_holdings
from adapters.ebsco.transformers.language import extract_languages
from adapters.ebsco.transformers.other_identifiers import extract_other_identifiers
from adapters.ebsco.transformers.parents import get_parents
from adapters.ebsco.transformers.production import extract_production
from adapters.ebsco.transformers.subjects import extract_subjects
from adapters.marc.transformers.alternative_titles import extract_alternative_titles
from adapters.marc.transformers.identifier import extract_id
from adapters.marc.transformers.title import extract_title
from adapters.transformers.axiell_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkRelations


class EbscoTransformer(MarcXmlTransformer):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]) -> None:
        super().__init__(
            adapter_store,
            changeset_ids=changeset_ids,
            identifier_type=Id(id="ebsco-alt-lookup"),
        )

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> VisibleSourceWork:
        work_id = extract_id(marc_record)
        work_data = WorkData(
            title=extract_title(marc_record),
            alternative_titles=extract_alternative_titles(marc_record),
            other_identifiers=extract_other_identifiers(marc_record),
            designation=extract_designation(marc_record),
            description=extract_description(marc_record),
            current_frequency=extract_current_frequency(marc_record),
            edition=extract_edition(marc_record),
            contributors=extract_contributors(marc_record),
            production=extract_production(marc_record),
            format=extract_format(marc_record),
            languages=extract_languages(marc_record),
            holdings=extract_holdings(marc_record),
            genres=extract_genres(marc_record),
            subjects=extract_subjects(marc_record),
        )

        relations = WorkRelations(ancestors=get_parents(marc_record))
        work_state = self.source_work_state(
            id_value=work_id,
            relations=relations,
            source_modified_time=source_modified_time,
        )

        return VisibleSourceWork(
            # This version is a required field downstream, but we
            # do not create versions in the EBSCO adapter, so we
            # use a timestamp-based version, to ensure downstream
            # events are always seen as newer than prior ones.
            version=int(
                dateutil.parser.parse(work_state.source_modified_time).timestamp()
            ),
            state=work_state,
            data=work_data,
        )
