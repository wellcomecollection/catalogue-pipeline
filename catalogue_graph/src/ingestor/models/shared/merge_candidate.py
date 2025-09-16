from pydantic import BaseModel

from ingestor.models.shared.identifier import Identifiers


class MergeCandidate(BaseModel):
    id: Identifiers
    reason: str
