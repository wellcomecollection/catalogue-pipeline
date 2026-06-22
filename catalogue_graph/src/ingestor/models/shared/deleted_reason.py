from pydantic import BaseModel

from utils.types import DeletedReasonType


class DeletedReason(BaseModel):
    type: DeletedReasonType
    info: str | None = None


DeletedFromSource = DeletedReason(
    type="DeletedFromSource", info="Marked as deleted from source"
)
SuppressedFromSource = DeletedReason(type="SuppressedFromSource")
