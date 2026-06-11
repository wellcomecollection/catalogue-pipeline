from abc import ABC, abstractmethod
from datetime import datetime

import dateutil.parser

from ingestor.models.shared.deleted_reason import DeletedReason
from models.pipeline.identifier import Id, WorkSourceIdentifier
from models.pipeline.source.work import DeletedSourceWork, SourceWorkState
from utils.timezone import convert_datetime_to_utc_iso


class SourceWorkBuilder(ABC):
    """
    Abstract base for assembling a source work from a single source record.

    Subclasses must implement `source_identifier_type`.

    For MARC-based sources, use `MarcXmlWorkBuilder` instead.
    """

    def __init__(self, source_id: str, last_modified: datetime) -> None:
        self.source_id = source_id
        self.last_modified = last_modified

    @property
    @abstractmethod
    def source_identifier_type(self) -> Id: ...

    @property
    def source_identifier(self) -> WorkSourceIdentifier:
        return WorkSourceIdentifier(
            identifier_type=self.source_identifier_type,
            value=self.source_id,
        )

    @property
    def source_modified_time(self) -> str:
        return convert_datetime_to_utc_iso(self.last_modified)

    @property
    def version(self) -> int:
        return int(dateutil.parser.parse(self.source_modified_time).timestamp())

    @property
    def work_state(self) -> SourceWorkState:
        return SourceWorkState(
            source_identifier=self.source_identifier,
            source_modified_time=self.source_modified_time,
            modified_time=convert_datetime_to_utc_iso(datetime.now()),
        )

    @property
    def deleted_work(self) -> DeletedSourceWork:
        return DeletedSourceWork(
            version=self.version,
            deleted_reason=DeletedReason(
                type="DeletedFromSource", info="Marked as deleted from source"
            ),
            state=self.work_state,
        )
