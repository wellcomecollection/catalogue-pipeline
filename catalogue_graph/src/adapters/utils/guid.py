"""GUID equivalence for Axiell reconciliation.

Axiell GUIDs (MARC 001) are UUIDs, and two spellings of the same UUID (a change
of case, or surrounding whitespace) identify the same record. The FOLIO sync
derives its record ids from the parsed UUID, and the id minter compares source
ids case-insensitively, so the reconcile step and the deletion-fact reader must
treat such spellings as one GUID too. Otherwise a spelling-only change reads as
a superseded GUID, and the resulting deletion fact removes the live record.
"""

from __future__ import annotations

import uuid


def guid_key(guid: str) -> str:
    """The key two GUIDs are compared on.

    The canonical UUID form when the GUID parses as a UUID, parsed the same way
    as the FOLIO sync's ``mapping.config._guid``. Anything else falls back to
    the stripped string, so a non-UUID GUID is still compared exactly.
    """
    value = guid.strip()
    try:
        return str(uuid.UUID(value))
    except ValueError:
        return value
