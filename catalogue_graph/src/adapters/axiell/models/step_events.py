"""Axiell adapter step event models.

These are type aliases to the generic OAI-PMH models for backwards compatibility.
New code should use the generic models from adapters.oai_pmh.models directly.
"""

from adapters.oai_pmh.models.step_events import (
    OAIPMHLoaderEvent,
    OAIPMHLoaderResponse,
    OAIPMHTriggerEvent,
)

# Type aliases for backwards compatibility
AxiellAdapterTriggerEvent = OAIPMHTriggerEvent
AxiellAdapterLoaderEvent = OAIPMHLoaderEvent
LoaderResponse = OAIPMHLoaderResponse

__all__ = [
    "AxiellAdapterTriggerEvent",
    "AxiellAdapterLoaderEvent",
    "LoaderResponse",
]
