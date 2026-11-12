"""Merge candidates for a FOLIO work, ported from SierraMergeCandidates.scala.

The reasons match the Sierra ones, because the merger matches on
"Physical/digitised Sierra work".
"""

import re

import structlog
from pymarc.record import Record

from adapters.transformers.folio.identifier import extract_hrid
from adapters.transformers.marc.common import all_non_empty_subfields, distinct
from adapters.transformers.marc.predecessor_identifier import SIERRA_SYSTEM_NUMBER_RE
from ingestor.models.shared.merge_candidate import MergeCandidate
from models.pipeline.identifier import Id, Identifiable, WorkSourceIdentifier

logger = structlog.get_logger(__name__)

UKLW_PREFIX_RE = re.compile(r"\((?i:UkLW)\)\s*(.+)")

# The migration moved the EBSCO id from 001 (with 003 "EBZ") into 035.
EBZ_PREFIX_RE = re.compile(r"\(EBZ\)(ebs\d+e)")

FULL_MIRO_ID = r"[A-Z][0-9]{7}[A-Z]{0,3}[0-9]?"
MIRO_ID_COMPONENTS_RE = re.compile(r"([A-Z])([0-9]{1,7})([A-Z]{0,3}[0-9]?)")
MIRO_URL_RES = [
    re.compile(
        rf"http://wellcomeimages\.org/indexplus/image/({FULL_MIRO_ID})(?:\.html){{0,2}}"
    ),
    re.compile(
        rf"http://wellcomeimages\.org/ixbin/hixclient\?MIROPAC=({FULL_MIRO_ID})"
    ),
    re.compile(
        rf"http://wellcomeimages\.org/ixbinixclient\.exe\?MIROPAC=({FULL_MIRO_ID})\.html\.html"
    ),
    re.compile(
        rf"http://wellcomeimages\.org/ixbinixclient\.exe\?image=({FULL_MIRO_ID})\.html"
    ),
]


def _merge_candidate(identifier_type: str, value: str, reason: str) -> MergeCandidate:
    return MergeCandidate(
        id=Identifiable.from_source_identifier(
            WorkSourceIdentifier(identifier_type=Id(id=identifier_type), value=value)
        ),
        reason=reason,
    )


def _physical_digitised_candidates(record: Record) -> list[MergeCandidate]:
    """The other half of a physical/digitised pair, from 776 ǂw `(UkLW)bNNN`.

    More than one distinct b-number is ambiguous, so it gives no candidate.
    """
    bnumbers = []
    for value in all_non_empty_subfields("776", "w", record):
        if match := UKLW_PREFIX_RE.fullmatch(value):
            bnumber = match.group(1).strip()
            if SIERRA_SYSTEM_NUMBER_RE.match(bnumber):
                bnumbers.append(bnumber)
            else:
                logger.warning("Invalid b-number in 776 ǂw", value=value)

    match distinct(bnumbers):
        case [bnumber]:
            return [
                _merge_candidate(
                    "sierra-system-number", bnumber, "Physical/digitised Sierra work"
                )
            ]
        case _:
            return []


def _miro_id_from_url(url: str) -> str | None:
    for pattern in MIRO_URL_RES:
        if match := pattern.fullmatch(url):
            return match.group(1)
    return None


def _miro_id_from_string(value: str) -> str | None:
    # 089 ids usually look like "V 13889".
    if match := MIRO_ID_COMPONENTS_RE.fullmatch(value.replace(" ", "")):
        prefix, digits, suffix = match.groups()
        return f"{prefix}{digits.zfill(7)}{suffix}"
    return None


def _strip_miro_suffix(miro_id: str) -> str:
    if match := MIRO_ID_COMPONENTS_RE.fullmatch(miro_id):
        return match.group(1) + match.group(2)
    return miro_id


def _miro_candidates(record: Record) -> list[MergeCandidate]:
    """Miro images from 089 ǂa and 962 ǂu, preferring a suffixed id to its stem."""
    ids = distinct(
        [
            miro_id
            for value in all_non_empty_subfields("089", "a", record)
            if (miro_id := _miro_id_from_string(value))
        ]
        + [
            miro_id
            for url in all_non_empty_subfields("962", "u", record)
            if (miro_id := _miro_id_from_url(url))
        ]
    )
    stems_with_suffixes = {
        _strip_miro_suffix(miro_id)
        for miro_id in ids
        if _strip_miro_suffix(miro_id) != miro_id
    }
    return [
        _merge_candidate("miro-image-number", miro_id, "Miro/Sierra work")
        for miro_id in ids
        if miro_id not in stems_with_suffixes
    ]


def _ebsco_candidates(record: Record) -> list[MergeCandidate]:
    """The EBSCO e-resource this record was replaced by, from 035 ǂa `(EBZ)ebsNNNe`."""
    ebsco_ids = distinct(
        match.group(1)
        for value in all_non_empty_subfields("035", "a", record)
        if (match := EBZ_PREFIX_RE.fullmatch(value))
    )
    return [
        _merge_candidate("ebsco-alt-lookup", ebsco_id, "EBSCO/Sierra e-resource")
        for ebsco_id in ebsco_ids
    ]


def _hrid_candidate(record: Record) -> MergeCandidate:
    """The HRID, which a METS record digitised from FOLIO also names, so both meet on it."""
    return _merge_candidate(
        "folio-instance-hrid", extract_hrid(record), "FOLIO/METS work"
    )


def extract_merge_candidates(record: Record) -> list[MergeCandidate]:
    return (
        [_hrid_candidate(record)]
        + _physical_digitised_candidates(record)
        + _miro_candidates(record)
        + _ebsco_candidates(record)
    )
