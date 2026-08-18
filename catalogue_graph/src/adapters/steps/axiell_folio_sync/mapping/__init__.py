"""
AxC → FOLIO mapping — the single home for *what an Axiell record becomes in FOLIO*.

Split into focused modules; this package re-exports the public surface so
``from ...mapping import X`` keeps working:

  • marc       MARCXML reading primitives + the CanonicalRecord model
  • config     the declarative AxC ⇄ FOLIO field table (FIELDS / FieldMap),
               normalization tables, defaults, hrid scheme
  • payloads   the typed FOLIO payload contracts (Instance / Holdings / Item …)
  • builders   the mapping logic (selection, extraction, _resolve, select_and_build)

The FOLIO-write outcome models (UpsertResult / GuidCascadeResult …) live in
``..results``.

RFC 090 design spec:
https://github.com/wellcomecollection/docs/tree/main/rfcs/090-axiell-folio-sync
"""

from .builders import (
    _assemble_payloads,
    _extract_record,
    _resolve,
    build_holdings,
    build_instance,
    build_item,
    is_selected_for_sync,
    select_and_build,
)
from .config import (
    AXIELL_LOCATION_NOTE_TYPE,
    DEFAULT_HOLDINGS_SOURCE,
    DEFAULT_LOAN_TYPE,
    DEFAULT_LOCATION,
    DEFAULT_MATERIAL_TYPE,
    FIELDS,
    HARVEST_FLAG_SPEC,
    HOLDINGS_SOURCE_FIELD,
    LOAN_TYPE_FIELD,
    LOCAL_IDENTIFIER_FIELD,
    LOCAL_IDENTIFIER_TYPE,
    LOCATION_FIELD,
    LOCATION_PREFIX_OVERRIDES,
    MARC_SOURCE,
    MATERIAL_TYPE,
    MATERIAL_TYPE_FIELD,
    RECORD_TYPE_ITEM,
    VERSION,
    FieldMap,
    _folio_location,
    _holdings_hrid,
    _instance_hrid,
    _item_hrid,
)
from .marc import CanonicalRecord, MappingError, extract, parse_xml
from .payloads import (
    Holdings,
    Identifier,
    IdRef,
    Instance,
    Item,
    MappedPayloads,
    Note,
    PayloadMeta,
    Status,
)

__all__ = [
    "AXIELL_LOCATION_NOTE_TYPE",
    "DEFAULT_HOLDINGS_SOURCE",
    "DEFAULT_LOAN_TYPE",
    "DEFAULT_LOCATION",
    "DEFAULT_MATERIAL_TYPE",
    "FIELDS",
    "HARVEST_FLAG_SPEC",
    "HOLDINGS_SOURCE_FIELD",
    "LOAN_TYPE_FIELD",
    "LOCAL_IDENTIFIER_FIELD",
    "LOCAL_IDENTIFIER_TYPE",
    "LOCATION_FIELD",
    "LOCATION_PREFIX_OVERRIDES",
    "MARC_SOURCE",
    "MATERIAL_TYPE",
    "MATERIAL_TYPE_FIELD",
    "RECORD_TYPE_ITEM",
    "VERSION",
    "CanonicalRecord",
    "FieldMap",
    "Holdings",
    "IdRef",
    "Identifier",
    "Instance",
    "Item",
    "MappedPayloads",
    "MappingError",
    "Note",
    "PayloadMeta",
    "Status",
    "_assemble_payloads",
    "_extract_record",
    "_folio_location",
    "_holdings_hrid",
    "_instance_hrid",
    "_item_hrid",
    "_resolve",
    "build_holdings",
    "build_instance",
    "build_item",
    "extract",
    "is_selected_for_sync",
    "parse_xml",
    "select_and_build",
]
