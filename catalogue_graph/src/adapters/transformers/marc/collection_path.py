"""Collection paths from MARC 773 (host item) and 774 (constituent unit) links.

A 773 with a ǂw control number says "this record is part of that one", and a
774 with a ǂw says "that record is part of this one". A record with either is
in a hierarchy and gets a collection path: `host/own` for a part, or `own` for
a root. A 773 ǂg says where in the host the record sits (e.g. "page 5"); it is
prefixed to the own segment as a token, giving `host/page_5_own`, so that parts
sort in order. It is not a segment of its own, as there is no record for it.
The host segment must be the identifier the host uses for itself, so that the
two paths join up.

https://www.loc.gov/marc/bibliographic/bd773.html
https://www.loc.gov/marc/bibliographic/bd774.html
"""

import re

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_blank_subfields
from adapters.transformers.marc.predecessor_identifier import SIERRA_SYSTEM_NUMBER_RE
from models.pipeline.collection_path import CollectionPath

logger = structlog.get_logger(__name__)

# Cataloguers prefix a local control number in ǂw with the Wellcome Library
# organisation code, which says whose number it is when records are exported.
LOCAL_IDENTIFIER_PREFIX = "(UkLW)"

NON_TOKEN_CHARACTERS = re.compile(r"[^0-9a-zA-Z_]")


def extract_collection_path(
    record: Record, own_identifier: str | None
) -> CollectionPath | None:
    """Build the collection path for a record linked by 773 ǂw or 774 ǂw.

    `own_identifier` is the identifier other records use for this one in their
    ǂw. Returns None for a record with no ǂw links, no identifier of its own, or
    whose only link is to itself.
    """
    host_fields = [f for f in record.get_fields("773") if non_blank_subfields(f, "w")]
    has_constituent_link = any(
        non_blank_subfields(f, "w") for f in record.get_fields("774")
    )
    if not host_fields and not has_constituent_link:
        return None

    if own_identifier is None:
        logger.warning(
            "Cannot create a collection path for a record with no identifier"
        )
        return None
    own_key = _path_key(own_identifier)

    if host_fields:
        host_field = host_fields[0]
        host_key = _path_key(_host_identifier(host_field))
        if host_key == own_key:
            logger.error(
                "Self-referential host link", field=str(host_field), identifier=own_key
            )
        else:
            path = f"{host_key}/{_related_part(host_field)}{own_key}"
            return CollectionPath(path=path)

    # No usable host: a record with constituents is the root of its hierarchy.
    if record.get_fields("774"):
        return CollectionPath(path=own_key)
    return None


def _host_identifier(host_field: Field) -> str:
    """The host's identifier from ǂw, preferring a unique identifier over a b-number.

    A 773 may repeat ǂw with both, and the host identifies itself by its unique
    identifier when it has one.
    """
    identifiers = [_strip_prefix(w) for w in non_blank_subfields(host_field, "w")]
    for identifier in identifiers:
        if not SIERRA_SYSTEM_NUMBER_RE.match(identifier):
            return identifier
    return identifiers[0]


def _strip_prefix(identifier: str) -> str:
    return identifier.strip().removeprefix(LOCAL_IDENTIFIER_PREFIX).strip()


def _path_key(identifier: str) -> str:
    # "/" separates path segments, so it cannot appear inside a key.
    return identifier.strip().replace("/", "|")


def _related_part(host_field: Field) -> str:
    """The ǂg related part as a token prefix for the own segment, or an empty string."""
    related_parts = host_field.get_subfields("g")
    if not related_parts:
        return ""
    return NON_TOKEN_CHARACTERS.sub("", related_parts[0].replace(" ", "_")) + "_"
