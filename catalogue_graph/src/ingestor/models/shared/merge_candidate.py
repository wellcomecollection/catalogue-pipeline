from pydantic import BaseModel

from models.pipeline.identifier import Identifiable, Identified


class MergeCandidate(BaseModel):
    id: Identified | Identifiable
    reason: str
