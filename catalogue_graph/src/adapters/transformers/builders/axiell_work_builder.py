from ingestor.models.display.location_type import LOCATION_LABEL_MAPPING
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import NotRequestable
from models.pipeline.collection_path import CollectionPath
from models.pipeline.identifier import Id, Unidentifiable, WorkSourceIdentifier
from models.pipeline.item import Item
from models.pipeline.location import LocationType, PhysicalLocation
from utils.timezone import convert_datetime_to_utc_iso
from utils.types import WorkType

from adapters.transformers.axiell.organisation_and_arrangement import (
    extract_hierarchical_level,
)
from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marc.last_transaction_time import (
    extract_last_transaction_time_to_datetime,
)
from adapters.transformers.marc.notes import extract_notes
from adapters.transformers.marc.predecessor_identifier import (
    extract_calm_predecessor_id,
)

from models.pipeline.identifier import Id, WorkSourceIdentifier
from models.pipeline.note import Note
from utils.timezone import convert_datetime_to_utc_iso


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
            raise ValueError(f"Missing work type on work '{self.source_identifier_value}'.")
        
        # TODO: Check if all of these levels are still used in Axiell 
        level_to_work_type: dict[str, WorkType] = {
            "collection": WorkType.Collection,
            "section": WorkType.Section,
            "subsection": WorkType.Section,
            "subsubsection": WorkType.Section,
            "subsubsubsection": WorkType.Section,
            "series": WorkType.Series,
            "subseries": WorkType.Series,
            "subsubseries": WorkType.Series,
            "subsubsubseries": WorkType.Series,
            "item": WorkType.Standard,
            "piece": WorkType.Standard,
        }

        work_type = level_to_work_type.get(level)
        if work_type is None:
            raise ValueError(f"Unknown hierarchical level '{level}' on work '{self.source_identifier_value}'.")

        return work_type
    
    @property
    def items(self) -> list[Item]:
        location_type = "closed-stores"
        return [
            Item(
                id=Unidentifiable, 
                locations=PhysicalLocation(
                    location_type=LocationType(id=location_type),
                    label=LOCATION_LABEL_MAPPING[location_type],
                    access_conditions=[
                        AccessCondition(method=NotRequestable, status=)
                    ]
                )
            )
        ]

    @property
    def notes(self) -> list[Note]:
        return extract_notes(self.record)

    # format = Some(CalmFormat(record)),
    # subjects = CalmSubjects(record),
    # languages = languages,
    # items = CalmItems(record),
    # contributors = CalmContributors(record),
    # description = description(record),
    # physicalDescription = physicalDescription(record),
    # production = production(record),
    # notes = CalmNotes(record) ++ languageNotes ++ CalmTermsOfUse(record)
