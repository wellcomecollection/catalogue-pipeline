from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marc.last_transaction_time import (
    extract_last_transaction_time_to_datetime,
)
from adapters.transformers.marc.predecessor_identifier import (
    extract_calm_predecessor_id,
)
from models.pipeline.identifier import Id, WorkSourceIdentifier
from utils.timezone import convert_datetime_to_utc_iso


class AxiellWorkBuilder(MarcXmlWorkBuilder):
    @property
    def source_identifier_type(self) -> Id:
        return Id(id="axiell-guid")

    @property
    def predecessor_identifier(self) -> WorkSourceIdentifier | None:
        if (value := extract_calm_predecessor_id(self.record)) is not None:
            return WorkSourceIdentifier(
                identifier_type=Id(id="calm-record-id"),
                value=value,
            )

        return None

    @property
    def source_modified_time(self) -> str:
        last_modified = extract_last_transaction_time_to_datetime(self.record)
        return convert_datetime_to_utc_iso(last_modified)
