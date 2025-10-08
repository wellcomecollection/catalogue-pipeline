from typing import Literal

from pydantic import BaseModel


class DeletedReason(BaseModel):
    type: Literal["DeletedFromSource", "SuppressedFromSource", "TeiDeletedInMerger"]
    info: str | None = None
