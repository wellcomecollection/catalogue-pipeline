from pydantic import BaseModel

from utils.types import InvisibleReasonType


class InvisibleReason(BaseModel):
    type: InvisibleReasonType
    info: str | None = None
    message: str | None = None
