"""
The reconciler delete path: suppress or hard-delete a superseded GUID's records.

Both ``suppress_by_guid`` (reversible) and ``delete_by_guid`` (irreversible) walk
the same child → parent cascade over a GUID's item / holdings / instance, applying
a single-entity op from ``entities.py``. The cascade aborts on the first failure so
a parent is never actioned while a failed child may still reference it. hrids map
directly from the GUID (``AxC-{entity}-{guid}``) — the same scheme the write path
uses — so no MARC parse or mapping is needed here.
"""

from __future__ import annotations

from collections.abc import Callable

import structlog

from ..folio import FolioInventoryOps
from ..mapping import _holdings_hrid, _instance_hrid, _item_hrid
from ..results import EntityResult, GuidCascadeResult, UpsertError
from .entities import _delete_entity, _suppress_entity

logger = structlog.get_logger(__name__)

# The cascade order is child → parent: (attr, search_path, list_key, hrid builder).
# For hard delete this order is mandatory — FOLIO refuses to delete a parent while
# a child still references it; for suppress it mirrors the reverse of create order.
_CASCADE_ENTITIES: list[tuple[str, str, str, Callable[[str], str]]] = [
    ("item", "/inventory/items", "items", _item_hrid),
    ("holdings", "/holdings-storage/holdings", "holdingsRecords", _holdings_hrid),
    ("instance", "/inventory/instances", "instances", _instance_hrid),
]

# An entity operation: resolve by hrid and apply an action (suppress / delete).
_EntityOp = Callable[..., EntityResult]


def _cascade_by_guid(
    guid: str,
    folio: FolioInventoryOps,
    entity_op: _EntityOp,
    *,
    dry_run: bool,
) -> GuidCascadeResult:
    """Apply ``entity_op`` to a superseded GUID's records, child-first.

    The single try/except is load-bearing for hard delete: if a child op fails,
    the cascade stops before the parent, so a parent is never actioned while a
    failed child may still reference it. Records already gone are skipped, but a
    *failed* hrid lookup is not a skip — it raises (see :func:`_find_by_hrid`) into
    this handler, so an outage during the lookup aborts and records an error rather
    than reporting a still-live record as a clean deletion. A hrid maps directly
    from the GUID (``AxC-{entity}-{guid}``), the same scheme the upsert path writes,
    so no MARC parse or mapping is needed here.
    """
    result = GuidCascadeResult(guid=guid)
    try:
        for attr, search_path, list_key, hrid_of in _CASCADE_ENTITIES:
            setattr(
                result,
                attr,
                entity_op(
                    folio,
                    search_path=search_path,
                    list_key=list_key,
                    write_path_prefix=search_path,
                    hrid=hrid_of(guid),
                    dry_run=dry_run,
                ),
            )
    except Exception as exc:
        result.errors.append(UpsertError(type="api", detail=str(exc)))
        logger.error(
            "reconcile cascade error", guid=guid, error=str(exc), exc_info=True
        )

    return result


def suppress_by_guid(
    guid: str,
    folio: FolioInventoryOps,
    *,
    dry_run: bool = False,
) -> GuidCascadeResult:
    """Soft-suppress the FOLIO records for a superseded GUID, child-first.

    Reversible and auditable: sets discoverySuppress (and staffSuppress on the
    instance) rather than hard-deleting. Idempotent — a redelivered fact re-sets
    flags already true.
    """
    return _cascade_by_guid(guid, folio, _suppress_entity, dry_run=dry_run)


def delete_by_guid(
    guid: str,
    folio: FolioInventoryOps,
    *,
    dry_run: bool = False,
) -> GuidCascadeResult:
    """Hard-delete the FOLIO records for a superseded GUID, child-first.

    Irreversible. The child-first order is mandatory (FOLIO enforces referential
    integrity), and the cascade aborts if a child delete fails so a parent is
    never deleted while a child remains. The inventory client treats a 404 on the
    DELETE as a no-op, so redelivered facts and races are handled cleanly.
    """
    return _cascade_by_guid(guid, folio, _delete_entity, dry_run=dry_run)
