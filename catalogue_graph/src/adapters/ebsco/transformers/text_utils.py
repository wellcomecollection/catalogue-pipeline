from __future__ import annotations

"""Shared text normalisation helpers for MARC transformers.

These mirror the Scala TextNormalisation behaviour we need here:
- Trailing punctuation trimming on concept labels (.,;:) while preserving internal punctuation
- Whitespace collapsing & lowercasing for stable identifier values

Note: We deliberately keep the broader punctuation trimming (.,;:) as per
existing Python implementation rather than the Scala period-only trimming,
per project decision.
"""

_TRAILING_PUNCTUATION = ".,;: "  # Broader than Scala which only trims period.


def clean_concept_label(value: str) -> str:
    """Normalise a raw concept label by removing trailing punctuation & trimming."""
    return value.rstrip(_TRAILING_PUNCTUATION).strip()


def normalise_identifier_value(label: str) -> str:
    """Collapse internal whitespace & lowercase for identifier and matching purposes."""
    return " ".join(label.split()).lower()
