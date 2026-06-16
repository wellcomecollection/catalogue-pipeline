from typing import get_args

from adapters.transformers.axiell.access_status import extract_access_status
from adapters.transformers.axiell.organisation_and_arrangement import (
    extract_hierarchical_level,
)
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
from models.pipeline.access_status import AccessStatusValue
from models.pipeline.collection_path import CollectionPath
from models.pipeline.identifier import Id, Unidentifiable, WorkSourceIdentifier
from models.pipeline.item import Item
from models.pipeline.location import ClosedStores, PhysicalLocation
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from utils.timezone import convert_datetime_to_utc_iso
from utils.types import WorkType


class AxiellWorkBuilder(MarcXmlWorkBuilder):
    """Work builder for Axiell (MARC XML) records."""

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
        """Reads from MARC 005 (last transaction time) rather than the adapter row's last_modified."""
        last_modified = extract_last_transaction_time_to_datetime(self.record)
        return convert_datetime_to_utc_iso(last_modified)

    @property
    def collection_path(self) -> CollectionPath:
        ref_no, alt_ref_no = None, None

        for identifier in self.other_identifiers:
            if identifier.identifier_type.id == "calm-ref-no":
                ref_no = identifier.value
            elif identifier.identifier_type.id == "calm-altref-no":
                alt_ref_no = identifier.value

        if ref_no is None:
            raise ValueError(f"Missing RefNo on work '{self.source_identifier_value}'.")

        return CollectionPath(path=ref_no, label=alt_ref_no)

    @property
    def reference_number(self) -> str | None:
        return self.collection_path.label

    @property
    def work_type(self) -> WorkType:
        level = extract_hierarchical_level(self.record)

        if not level:
            raise ValueError(
                f"Missing work type on work '{self.source_identifier_value}'."
            )

        # TODO: Check if all of these levels are still used in Axiell
        level_to_work_type: dict[str, WorkType] = {
            "collection": "Collection",
            "section": "Section",
            "subsection": "Section",
            "subsubsection": "Section",
            "subsubsubsection": "Section",
            "series": "Series",
            "subseries": "Series",
            "subsubseries": "Series",
            "subsubsubseries": "Series",
            "item": "Standard",
            "piece": "Standard",
        }

        work_type = level_to_work_type.get(level)
        if work_type is None:
            raise ValueError(
                f"Unknown hierarchical level '{level}' on work '{self.source_identifier_value}'."
            )

        return work_type

    @property
    def items(self) -> list[Item]:
        access_conditions = []
        access_status = extract_access_status(self.record)

        # TODO: Translate statuses
        if access_status and access_status in get_args(AccessStatusValue):
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

    # format = Some(CalmFormat(record)),
    # languages = languages,
    # description = description(record),
    # notes = CalmNotes(record) ++ languageNotes ++ CalmTermsOfUse(record)
    # subjects = CalmSubjects(record),
    # contributors = CalmContributors(record),
