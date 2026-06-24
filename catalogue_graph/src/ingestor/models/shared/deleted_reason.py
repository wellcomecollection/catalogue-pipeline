from pydantic import BaseModel

from utils.types import DeletedReasonType


class DeletedReason(BaseModel):
    type: DeletedReasonType
    info: str | None = None


class SuppressedFromSource(DeletedReason):
    type: DeletedReasonType = "SuppressedFromSource"


class DeletedFromSource(DeletedReason):
    type: DeletedReasonType = "DeletedFromSource"
    info: str | None = "Marked as deleted from source"
