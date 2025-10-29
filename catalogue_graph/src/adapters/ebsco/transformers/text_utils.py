from __future__ import annotations

import re

from utils.types import RawConceptType

"""Shared text normalisation helpers for MARC transformers.

Updated to replicate Scala ConceptsTransformer type-specific trailing punctuation semantics.

Scala rules (see ConceptsTransformer.scala):
    - Concept & GenreConcept labels: trim a single trailing period (not ellipses), then whitespace.
    - Genre work-level label additionally replaces 'Electronic Books' with 'Electronic books'.
    - Agent/Person/Organisation/Meeting labels: trim a trailing comma.
    - Place labels: trim a trailing colon.
    - Period labels: no punctuation trimming here (identifiers use a preprocessed version upstream).

Identifier values are now derived exclusively via Identifiable.identifier_from_text; we no longer perform a separate
whitespace-collapsing pass (previous normalise_identifier_value removed).
"""


def trim_trailing_period(label: str) -> str:
    """Remove a single trailing period but not ellipses, mirroring Scala trimTrailingPeriod.

    Scala regex (conceptually): /([^\\.])\\.\\s*$/ replacement "$1"; then trim trailing whitespace.
    We replicate by first substituting, then stripping trailing whitespace.
    Examples:
      Title.   -> Title
      Title..  -> Title.   (remove only one)
      Title... -> Title... (ellipsis preserved)
    """
    result = re.sub(r"([^\.])\.\s*$", r"\1", label)
    return re.sub(r"\s*$", "", result)


def trim_trailing(label: str, char: str) -> str:
    """Remove the given trailing character and any surrounding whitespace (single instance), mirroring Scala trimTrailing.

    Example: trim_trailing("Name,  ", ",") -> "Name"
    We construct a regex similar to Scala's dynamic one.
    """
    pattern = r"\s*" + re.escape(char) + r"\s*$"
    return re.sub(pattern, "", label)


def normalise_label(label: str, concept_type: RawConceptType) -> str:
    """Apply type-specific trailing punctuation trimming matching Scala semantics.

    Ellipses preservation: only remove a single trailing period if *not* part of an ellipsis.
    Comma / colon trimming removes a single trailing instance plus surrounding whitespace.
    Genre special-case: after period trimming & whitespace normalisation, replace
    'Electronic Books' with 'Electronic books'.
    Period: return unchanged (aside from outer whitespace trimming) – preprocessing for IDs handled elsewhere.
    """
    s = label.strip()

    if concept_type in ["Concept", "GenreConcept", "Subject", "Period"]:
        s = trim_trailing_period(s)
    elif concept_type in ["Agent", "Person", "Organisation", "Meeting"]:
        s = trim_trailing(s, ",")
    elif concept_type == "Place":
        s = trim_trailing(s, ":")

    # Replace exact label 'Electronic Books' (after period trimming) with sentence case form.
    if concept_type == "GenreConcept" and s == "Electronic Books":
        s = "Electronic books"

    return s


## NOTE: normalise_identifier_value removed; use Identifiable.identifier_from_text for label-derived identifiers.
