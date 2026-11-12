"""Collection paths from MARC 773 (host item) and 774 (constituent unit) links.

A 773 with a ǂw control number says "this record is part of that one", and a
774 with a ǂw says "that record is part of this one". A record with either is
in a hierarchy and gets a collection path: `host/own` for a part, or `own` for
a root. The host segment must be the identifier the host uses for itself, so
that the two paths join up.

A 773 ǂg says where in the host the record sits (e.g. "page 5"). There is no
record for "page 5", so it does not become a segment of its own. Instead, it is
turned into a token and placed before the own identifier in the last segment,
giving `host/page_5_own`, so that a host's parts sort in order.

A ǂw value is prefixed with the code of the organisation that issued the
control number: (UkLW) for a Wellcome record, (OCoLC) for an OCLC record, and
so on. Only a link to a Wellcome record can join up with a path, so links
carrying any other organisation's code are ignored.

https://www.loc.gov/marc/bibliographic/bd773.html
https://www.loc.gov/marc/bibliographic/bd774.html
"""

import re

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_blank_subfields, non_empty
from adapters.transformers.marc.predecessor_identifier import SIERRA_SYSTEM_NUMBER_RE
from models.pipeline.collection_path import CollectionPath

logger = structlog.get_logger(__name__)

LOCAL_ORGANISATION_CODE = "(UkLW)"

NON_TOKEN_CHARACTERS = re.compile(r"[^0-9a-zA-Z_]")


def extract_collection_path(
    record: Record, own_identifier: str | None
) -> CollectionPath | None:
    """Build the collection path for a record linked by 773 ǂw or 774 ǂw.

    Returns None for a record with no links to Wellcome records, no identifier
    of its own, or whose only link is to itself.

    `own_identifier` is the identifier other records use to reference this one
    in their 773/774 ǂw.
    """
    host_link = next(
        (
            (field, identifiers)
            for field in record.get_fields("773")
            if (identifiers := _local_identifiers(field))
        ),
        None,
    )
    has_constituents = any(
        _local_identifiers(field) for field in record.get_fields("774")
    )
    if host_link is None and not has_constituents:
        return None

    if own_identifier is None:
        logger.warning(
            "Cannot create a collection path for a record with no identifier"
        )
        return None
    own_key = _path_key(own_identifier)

    if host_link is not None:
        host_field, host_identifiers = host_link
        host_key = _path_key(_host_identifier(host_identifiers))
        if host_key != own_key:
            path = f"{host_key}/{_related_part(host_field)}{own_key}"
            return CollectionPath(path=path)
        logger.error(
            "Self-referential host link", field=str(host_field), identifier=own_key
        )

    # A record with constituents and no usable host is the root of its hierarchy.
    return CollectionPath(path=own_key) if has_constituents else None


def _local_identifiers(field: Field) -> list[str]:
    """The ǂw values that identify Wellcome records, without the (UkLW) code.

    A value with no organisation code is taken to be a Wellcome identifier too.
    A bare (UkLW) with no identifier after it is left out.
    """
    identifiers = [w.strip() for w in non_blank_subfields(field, "w")]
    return non_empty(
        identifier.removeprefix(LOCAL_ORGANISATION_CODE).strip()
        for identifier in identifiers
        if identifier.startswith(LOCAL_ORGANISATION_CODE)
        or not identifier.startswith("(")
    )


def _host_identifier(identifiers: list[str]) -> str:
    """The host's identifier, preferring a unique identifier over a b-number.

    A 773 may repeat ǂw with both, and the host identifies itself by its unique
    identifier when it has one.
    """
    for identifier in identifiers:
        if not SIERRA_SYSTEM_NUMBER_RE.match(identifier):
            return identifier
    return identifiers[0]


def _path_key(identifier: str) -> str:
    # "/" separates path segments, so it cannot appear inside a key.
    return identifier.strip().replace("/", "|")


def _related_part(host_field: Field) -> str:
    """The ǂg related part as a token prefix for the own segment, or an empty string."""
    related_parts = host_field.get_subfields("g")
    if not related_parts:
        return ""
    return NON_TOKEN_CHARACTERS.sub("", related_parts[0].replace(" ", "_")) + "_"
