from __future__ import annotations

import re

from models.pipeline.collection_path import CollectionPath
from models.pipeline.id_label import IdLabel

# Maps collection path label prefixes (top-level reference number segments) to human-readable labels.
COLLECTION_PATH_PREFIX_LABELS: dict[str, str] = {
    "AAU": "Aids Archive UK",
    "ART": "Art",
    "ES": "Exhibitions and shows",
    "GC": "General collections",
    "GP": "General practitioner",
    "GRL": "Genome Research Ltd",
    "OH": "Oral History",
    "PBL": "Published grey literature",
    "PP": "Personal Papers",
    "PSY": "British Psychological Society",
    "SA": "Societies and Associations",
    "TP": "Audio Collections",
    "WA": "Wellcome Archives",
    "WF": "Wellcome Foundation",
    "WTI": "Wellcome Tropical Institute",
    "WT": "Wellcome Trust",
}

# Prefixes that may appear with a trailing number (e.g. OH1, TP2) but belong under the base code.
_NUMERIC_SUFFIX_PREFIXES = {"OH", "TP"}


class ArchiveType(IdLabel):
    @classmethod
    def from_collection_path(cls, path: CollectionPath | None) -> ArchiveType | None:
        """Return the normalised archive type for a collection path.

        Extracts the leading segment (e.g. "OH1" from "OH1/B/3"), strips trailing
        digits for known numbered sub-collections (e.g. "OH1" -> "OH"), then looks
        up the result in the prefix-to-label mapping.  Returns None when the prefix
        is not recognised.
        """
        # We use `label` rather than `path` because, for known archive types, only
        # `label` separates the prefix from the rest of the reference number with a
        # "/" (e.g. label "PP/CRI" vs path "PPCRI"). `path` has no such separator.
        if not path or not path.label:
            return None

        raw_prefix = path.label.split("/")[0]
        match = re.fullmatch(r"([A-Z]+)(\d+)", raw_prefix)
        normalised = (
            match.group(1)
            if match and match.group(1) in _NUMERIC_SUFFIX_PREFIXES
            else raw_prefix
        )

        label = COLLECTION_PATH_PREFIX_LABELS.get(normalised)
        if label is None:
            return None

        return cls(id=normalised, label=label)
