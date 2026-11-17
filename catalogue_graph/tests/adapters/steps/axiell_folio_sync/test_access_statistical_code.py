"""The access statistical code on the item, from 506$f and 540$a together.

The agreed rows of the Access and Ordering status mapping
(wellcomecollection/platform#6862). The item also keeps the AXIELL marker; the
instance and holdings carry the marker only.
"""

from __future__ import annotations

import pytest

from adapters.steps.axiell_folio_sync.mapping import (
    ACCESS_STATISTICAL_CODE,
    ACCESS_STATISTICAL_CODES,
    MappingError,
    select_and_build,
)
from adapters.steps.axiell_folio_sync.mapping.payloads import MappedPayloads

from .test_item_mapping import FakeRefCache, _record_with_access

MARKER_ID = "axiell-uuid"


class TenantRefCache(FakeRefCache):
    """Resolves statistical codes the way the tenant would: each code its own id,
    and a code the tenant lacks to None."""

    def __init__(self, missing: frozenset[str] = frozenset()) -> None:
        self.missing = missing

    def resolve_statistical_code(self, code: str | None) -> str | None:
        if code is None or code in self.missing:
            return None
        if code == "AXIELL":
            return MARKER_ID
        return f"{code.lower().replace(' ', '-')}-uuid"


def _id(code: str) -> str:
    return f"{code.lower().replace(' ', '-')}-uuid"


def _mapped(
    category: str | None, use_restriction: str | None, ref: TenantRefCache | None = None
) -> MappedPayloads:
    mapped = select_and_build(
        _record_with_access(category, use_restriction),
        ref or TenantRefCache(),  # type: ignore[arg-type]
    )
    assert mapped is not None
    return mapped


def _item_codes(category: str | None, use_restriction: str | None) -> list[str]:
    return _mapped(category, use_restriction).item.statisticalCodeIds or []


# Written out from the issue rather than read from the config, so a change to
# either side fails here.
AGREED = [
    ("OPEN", "Online request", "ONLINE REQUEST"),
    ("OPEN", "Manual request", "MANUAL REQUEST"),
    ("OPEN", "By appointment", "BY APPOINTMENT"),
    ("OPENWITHADVISORY", "Online request", "ONLINE REQUEST"),
    ("OPENWITHADVISORY", "Manual request", "MANUAL REQUEST"),
    ("OPENWITHADVISORY", "By appointment", "BY APPOINTMENT"),
    ("OPENWITHADVISORY", "Unavailable", "UNAVAILABLE"),
    ("RESTRICTED", "Restricted", "RESTRICTED"),
    ("RESTRICTED", "Online request", "RESTRICTED"),
    ("RESTRICTED", "By appointment", "BY APPOINTMENT"),
    ("RESTRICTED", "Unavailable", "UNAVAILABLE"),
    ("PERMISSIONREQUIRED", "By approval", "DONOR PERMISSION"),
    ("PERMISSIONREQUIRED", "By appointment", "DONOR PERMISSION"),
    ("SAFEGUARDED", "By approval", "SAFEGUARDED"),
    ("SAFEGUARDED", "By appointment", "SAFEGUARDED"),
    ("CLOSED", "Unavailable", "CLOSED"),
    ("DATAISSUES", "Online request", "ONLINE REQUEST"),
    ("DATAISSUES", "Manual request", "MANUAL REQUEST"),
    ("DATAISSUES", "By appointment", "BY APPOINTMENT"),
    ("DATAISSUES", "Unavailable", "UNAVAILABLE"),
    ("DATAISSUES", "By approval", "DONOR PERMISSION"),
    ("DATAISSUES", "Restricted", "ONLINE REQUEST"),
    ("MISSING", "Online request", "ONLINE REQUEST"),
    ("MISSING", "Manual request", "MANUAL REQUEST"),
    ("MISSING", "By appointment", "BY APPOINTMENT"),
    ("MISSING", "Unavailable", "UNAVAILABLE"),
    ("MISSING", "By approval", "DONOR PERMISSION"),
    ("MISSING", "Restricted", "ONLINE REQUEST"),
]


@pytest.mark.parametrize(("category", "use_restriction", "code"), AGREED)
def test_an_agreed_pair_gives_its_access_code(
    category: str, use_restriction: str, code: str
) -> None:
    assert _item_codes(category, use_restriction) == [MARKER_ID, _id(code)]


def test_the_table_holds_exactly_the_agreed_pairs() -> None:
    table = {
        (category, term, code)
        for category, by_term in ACCESS_STATISTICAL_CODE.items()
        for term, code in by_term.items()
    }
    assert table == set(AGREED)


def test_matching_is_case_insensitive_on_both_fields() -> None:
    assert _item_codes("Restricted", "ONLINE REQUEST") == [
        MARKER_ID,
        _id("RESTRICTED"),
    ]


@pytest.mark.parametrize(
    ("category", "use_restriction"),
    [
        # Rows still under discussion are left without a code, not guessed at.
        ("OPEN", "Unavailable"),
        ("MISSING", "Missing"),
        ("DEACCESSIONED", "Unavailable"),
        # Not a row at all: the access category alone does not decide.
        ("CLOSED", "By appointment"),
        # Either field absent.
        ("OPEN", None),
        (None, "Online request"),
    ],
)
def test_a_pair_outside_the_table_gets_the_marker_only(
    category: str | None, use_restriction: str | None
) -> None:
    assert _item_codes(category, use_restriction) == [MARKER_ID]


def test_the_instance_and_holdings_carry_the_marker_only() -> None:
    mapped = _mapped("OPEN", "Online request")

    assert mapped.instance.statisticalCodeIds == [MARKER_ID]
    assert mapped.holdings.statisticalCodeIds == [MARKER_ID]


def test_an_access_code_the_tenant_lacks_fails_the_record() -> None:
    ref = TenantRefCache(missing=frozenset({"CLOSED"}))

    with pytest.raises(MappingError, match="access statistical code 'CLOSED'"):
        _mapped("CLOSED", "Unavailable", ref)


def test_the_meta_lists_every_access_code_the_tenant_has() -> None:
    """The upsert replaces any of these already on the item. A code the tenant
    lacks is left out, and the marker is never among them."""
    ref = TenantRefCache(missing=frozenset({"DONOR PERMISSION"}))

    ids = _mapped("OPEN", "Online request", ref).meta.access_statistical_code_ids

    assert set(ids) == {
        _id(code) for code in ACCESS_STATISTICAL_CODES - {"DONOR PERMISSION"}
    }
    assert MARKER_ID not in ids
