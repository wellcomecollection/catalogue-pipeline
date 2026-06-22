from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marc.predecessor_identifier import (
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

    def _is_suppressed(self) -> bool:
        """Check if a FOLIO Instance is marked as suppressed in its MARC data.

        FOLIO marks suppressed Instances with $t = 1 in MARC field 999
        when "Transfer suppressed records with discovery flag value" is enabled
        in FOLIO's OAI-PMH settings.

        Returns:
            True if suppression marker is found in field 999, False otherwise.
        """
        for field in self.record.fields:
            if field.tag == "999" and "1" in field.get_subfields("t"):
                return True

        return False

    def is_deleted(self, row: dict) -> bool:
        """Check if a FOLIO record should be treated as deleted.

        A FOLIO record is deleted if:
        1. OAI-PMH hard deletion: row.deleted = True
        2. FOLIO discovery suppression: MARC field 999 contains $t = 1

        Args:
            row: The adapter store row.

        Returns:
            True if the record is hard-deleted or suppressed.
        """
        return row.get("deleted", False) or self._is_suppressed()
