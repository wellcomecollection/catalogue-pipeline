import structlog

from adapters.transformers.axiell.access_status import extract_access_status
from adapters.transformers.axiell.catalogue_status import (
    AxiellCatalogueStatus,
    extract_catalogue_status,
)
from adapters.transformers.axiell.contributors import extract_contributors
from adapters.transformers.axiell.format import extract_format
from adapters.transformers.axiell.organisation_and_arrangement import (
    extract_work_type,
)
from adapters.transformers.axiell.subjects import extract_subjects
from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.ebsco.production import (
    extract_production,
)
from adapters.transformers.marc.last_transaction_time import (
    extract_last_transaction_time_to_datetime,
)
from adapters.transformers.marc.notes import extract_notes
from adapters.transformers.marc.physical_description import extract_physical_description
from adapters.transformers.marc.predecessor_identifier import (
    extract_calm_predecessor_id,
)
from ingestor.models.display.location_type import LOCATION_LABEL_MAPPING
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import NotRequestable
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Contributor, Subject
from models.pipeline.format import Format
from models.pipeline.identifier import Id, Unidentifiable, WorkSourceIdentifier
from models.pipeline.item import Item
from models.pipeline.location import ClosedStores, PhysicalLocation
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from utils.timezone import convert_datetime_to_utc_iso
from utils.types import WorkType

logger = structlog.get_logger(__name__)


NON_SUPPRESSED_STATUSES: set[AxiellCatalogueStatus] = {
    "catalogued",
    "partially complete",
}


class AxiellWorkBuilder(MarcXmlWorkBuilder):
    """Work builder for Axiell (MARC XML) records."""

    @property
    def _should_suppress(self) -> bool:
        # Records prefixed with AMSG (Archives and Manuscripts Resource Guides) are not
        # actual archives but instead guides for researchers, so we suppress them here.
        ref_no = self.reference_number
        if ref_no is not None and ref_no.startswith("AMSG"):
            return True

        catalogue_status = extract_catalogue_status(self.record)

        # If a record does not have a status, or if its status is not listed in NON_SUPPRESSED_STATUSES, we suppress it
        return catalogue_status not in NON_SUPPRESSED_STATUSES

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
    def format(self) -> Format:
        return extract_format(self.record)

    @property
    def source_modified_time(self) -> str:
        """Reads from MARC 005 (last transaction time) rather than the adapter row's last_modified."""
        last_modified = extract_last_transaction_time_to_datetime(self.record)
        return convert_datetime_to_utc_iso(last_modified)

    @property
    def collection_path(self) -> CollectionPath:
        ref_no, alt_ref_no = None, None

        # Search for calm-ref-no and calm-altref-no identifiers, extracted into the other_identifiers field
        for identifier in self.other_identifiers:
            if identifier.identifier_type.id == "calm-ref-no":
                ref_no = identifier.value
            elif identifier.identifier_type.id == "calm-altref-no":
                alt_ref_no = identifier.value

        # calm-ref-no is required
        if ref_no is None:
            raise ValueError(f"Missing RefNo on work '{self.source_identifier_value}'.")

        return CollectionPath(path=ref_no, label=alt_ref_no)

    @property
    def reference_number(self) -> str | None:
        return self.collection_path.label

    @property
    def work_type(self) -> WorkType:
        return extract_work_type(self.record)

    @property
    def items(self) -> list[Item]:
        access_conditions = []
        access_status = extract_access_status(self.record)

        if access_status:
            access_conditions.append(
                AccessCondition(method=NotRequestable, status=access_status)
            )

        return [
            Item(
                id=Unidentifiable(),
                locations=[
                    PhysicalLocation(
                        location_type=ClosedStores,
                        label=LOCATION_LABEL_MAPPING[ClosedStores.id],
                        access_conditions=access_conditions,
                    )
                ],
            )
        ]

    @property
    def notes(self) -> list[Note]:
        return extract_notes(self.record)

    @property
    def physical_description(self) -> str | None:
        return extract_physical_description(self.record)

    @property
    def production(self) -> list[ProductionEvent]:
        return extract_production(self.record)

    @property
    def contributors(self) -> list[Contributor]:
        return extract_contributors(self.record)

    @property
    def subjects(self) -> list[Subject]:
        return extract_subjects(self.record)

    # TODO: Remaining fields:
    # * languages
    # * notes (language notes, terms of use)
