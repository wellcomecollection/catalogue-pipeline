from typing import Literal

from models.pipeline.serialisable import SerialisableModel

AccessMethodValue = Literal[
    "OnlineRequest", "ManualRequest", "NotRequestable", "ViewOnline", "OpenShelves"
]


class AccessMethod(SerialisableModel):
    type: AccessMethodValue


OnlineRequest = AccessMethod(type="OnlineRequest")
ManualRequest = AccessMethod(type="ManualRequest")
NotRequestable = AccessMethod(type="NotRequestable")
ViewOnline = AccessMethod(type="ViewOnline")
OpenShelves = AccessMethod(type="OpenShelves")
