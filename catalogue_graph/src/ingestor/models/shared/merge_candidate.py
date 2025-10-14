from pydantic import BaseModel

from models.pipeline.identifier import Identified


class MergeCandidate(BaseModel):
    id: Identified
    reason: str
