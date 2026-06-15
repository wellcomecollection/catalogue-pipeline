from models.pipeline.access_method import AccessMethod
from models.pipeline.access_status import AccessStatus
from models.pipeline.serialisable import SerialisableModel


class AccessCondition(SerialisableModel):
    method: AccessMethod
    status: AccessStatus | None = None
    terms: str | None = None
    note: str | None = None
