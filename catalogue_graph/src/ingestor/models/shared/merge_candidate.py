from pydantic import BaseModel

from models.pipeline.identifier import Identifiers


class MergeCandidate(BaseModel):
    id: Identifiers
    reason: str
