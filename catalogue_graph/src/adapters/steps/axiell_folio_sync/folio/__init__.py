"""
FOLIO client seam for the Axiell → FOLIO sync path.

  • callables   FolioInventoryOps — the typed Inventory-ops protocol that
                decouples this package from the concrete FOLIO client
  • ref_cache   RefCache — tenant reference data (locations, material/loan types,
                holdings sources, note types, instance type) fetched once and cached
"""

from .callables import FolioInventoryOps
from .ref_cache import RefCache

__all__ = [
    "FolioInventoryOps",
    "RefCache",
]
