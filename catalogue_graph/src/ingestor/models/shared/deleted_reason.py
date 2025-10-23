from pydantic import BaseModel

from utils.types import DeletedReasonType


class DeletedReason(BaseModel):
    type: DeletedReasonType
    info: str | None = None
