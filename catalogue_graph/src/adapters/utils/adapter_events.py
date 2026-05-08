"""Backward compatibility shim for adapter events.

The adapter event models have been moved to adapters.models.events.
This file maintains backward compatibility with existing imports.
"""

from adapters.models.events import BaseAdapterEvent, BaseLoaderResponse

__all__ = ["BaseAdapterEvent", "BaseLoaderResponse"]

