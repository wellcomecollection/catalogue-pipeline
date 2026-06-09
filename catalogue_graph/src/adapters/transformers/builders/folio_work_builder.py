from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.folio.predecessor_identifier import (
    extract_sierra_predecessor_id,
)
from models.pipeline.identifier import Id, WorkSourceIdentifier


class FolioWorkBuilder(MarcXmlWorkBuilder):
    @property
    def source_identifier_type(self) -> Id:
        return Id(id="folio-instance")

    @property
    def predecessor_identifier(self) -> WorkSourceIdentifier | None:
        if (value := extract_sierra_predecessor_id(self.record)) is not None:
            return WorkSourceIdentifier(
                identifier_type=Id(id="sierra-system-number"),
                value=value,
            )

        return None
