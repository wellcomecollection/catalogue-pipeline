from adapters.extractors.oai_pmh.folio.enrichment.models import FolioEnrichedInstance
from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marc.predecessor_identifier import (
    extract_sierra_predecessor_id,
)
from ingestor.models.shared.deleted_reason import SuppressedFromSource
from models.pipeline.identifier import (
    Id,
    Identifiable,
    SourceIdentifier,
    WorkSourceIdentifier,
)
from models.pipeline.item import Item
from models.pipeline.source.work import DeletedSourceWork, VisibleSourceWork

# The source-identifier type for a FOLIO item. The id-minter turns this plus the
# item UUID into a stable canonical id for the public catalogue (RFC 088 / Option C).
FOLIO_ITEM_IDENTIFIER_TYPE = Id(id="folio-item")


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

    @property
    def items(self) -> list[Item]:
        """Build items from the joined enrichment payload.

        Each item carries a `folio-item` source identifier with its inventory UUID,
        so the id-minter can assign a stable canonical id. Without enrichment content
        (e.g. an instance not yet enriched, or a full reindex before the items store
        is populated) this falls back to no items rather than guessing from MARC 952.
        """
        if not self.enrichment_content:
            return []

        enriched = FolioEnrichedInstance.from_store_content(self.enrichment_content)
        return [
            Item(
                id=Identifiable.from_source_identifier(
                    SourceIdentifier(
                        identifier_type=FOLIO_ITEM_IDENTIFIER_TYPE,
                        ontology_type="Item",
                        value=item.id,
                    )
                ),
                title=item.copy_number,
            )
            for item in enriched.items
        ]

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

    def transform_work(self) -> VisibleSourceWork | DeletedSourceWork:
        if self._is_suppressed():
            return self.transform_deleted_work(
                deleted_reason=SuppressedFromSource(info="Folio")
            )
        else:
            return self.transform_visible_work()
