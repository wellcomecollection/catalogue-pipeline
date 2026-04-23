from models.pipeline.identifier import (
    SourceIdentifier,
)
from models.pipeline.serialisable import SerialisableModel


class ImageState(SerialisableModel):
    canonical_id: str
    source_identifier: SourceIdentifier

    def id(self) -> str:
        return self.canonical_id
