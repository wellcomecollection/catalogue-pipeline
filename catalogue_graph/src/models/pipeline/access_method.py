from typing import Literal

from models.pipeline.serialisable import SerialisableModel

AccessMethodValue = Literal["OnlineRequest", "ManualRequest", "NotRequestable", "ViewOnline", "OpenShelves"] 


class AccessMethod(SerialisableModel):
    type: AccessMethodValue


ViewOnline = AccessMethod(type="ViewOnline")
NotRequestable = AccessMethod(type="NotRequestable")
