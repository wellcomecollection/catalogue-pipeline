"""
FOLIO Inventory upsert layer.

The create/update path enforces write order Instance → Holdings → Item; for a
record flagged deleted it soft-suppresses the item only (discoverySuppress). The
reconciler cascade actions a superseded GUID's records child → parent:
``suppress_by_guid`` sets discoverySuppress on all three plus staffSuppress on the
instance (the only entity with that field), while ``delete_by_guid`` hard-deletes.
``dry_run=True`` resolves existing records and plans actions but makes no writes.

Split into focused modules; this package re-exports the public entry points so
``from ...upsert import ...`` keeps working:

  • entities   single-entity ops (find / create-update / suppress / delete)
  • writer     upsert_from_payloads — the create/update path
  • reconcile  suppress_by_guid / delete_by_guid — the guid-cascade delete path
"""

from .reconcile import delete_by_guid, suppress_by_guid
from .writer import upsert_from_payloads

__all__ = [
    "delete_by_guid",
    "suppress_by_guid",
    "upsert_from_payloads",
]
