from typing import Literal

from pydantic import BaseModel


class InvisibleReason(BaseModel):
    type: Literal[
        "CopyrightNotCleared",
        "SourceFieldMissing",
        "InvalidValueInSourceField",
        "UnlinkedHistoricalLibraryMiro",
        "UnableToTransform",
        "MetsWorksAreNotVisible",
    ]
    info: str | None = None
    message: str | None = None
