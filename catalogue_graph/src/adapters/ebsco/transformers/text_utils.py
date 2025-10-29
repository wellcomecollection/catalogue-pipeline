from __future__ import annotations

import re

from utils.types import ConceptType as ConceptTypeLike

"""Shared text normalisation helpers for MARC transformers.

Updated to replicate Scala ConceptsTransformer type-specific trailing punctuation semantics.

Scala rules (see ConceptsTransformer.scala):
  - Concept & GenreConcept labels: trim a single trailing period (not ellipses), then whitespace.
  - Genre work-level label additionally replaces 'Electronic Books' with 'Electronic books'.
  - Agent/Person/Organisation/Meeting labels: trim a trailing comma.
  - Place labels: trim a trailing colon.
  - Period labels: no punctuation trimming here (identifiers use a preprocessed version upstream).

We keep whitespace collapsing for identifier values.
"""


def normalise_label(label: str, concept_type: ConceptTypeLike) -> str:
    """Apply type-specific trailing punctuation trimming matching Scala semantics.

    Ellipses preservation: only remove a single trailing period if *not* part of an ellipsis.
    Comma / colon trimming removes a single trailing instance plus surrounding whitespace.
    Genre special-case: after period trimming & whitespace normalisation, replace
    'Electronic Books' with 'Electronic books'.
    Period: return unchanged (aside from outer whitespace trimming) – preprocessing for IDs handled elsewhere.
    """
    s = label.strip()

    if concept_type in ["Concept", "Genre", "Subject", "Period"]:
        # Remove a single trailing period unless part of ellipsis (i.e. three periods)
        # Regex replicates Scala trimTrailingPeriod behaviour.
        s = re.sub(r"([^\.])\.\s*$", r"\1", s).rstrip()
    elif concept_type in ["Agent", "Person", "Organisation", "Meeting"]:
        # Trim trailing comma
        s = re.sub(r"\s*,\s*$", "", s)
    elif concept_type == "Place":
        # Trim trailing colon
        s = re.sub(r"\s*:\s*$", "", s)

    # Replace exact label 'Electronic Books' (after period trimming) with sentence case form.
    if concept_type == "Genre" and s == "Electronic Books":
        s = "Electronic books"

    return s


def normalise_identifier_value(label: str) -> str:
    """Collapse internal whitespace & lowercase for identifier and matching purposes."""
    return " ".join(label.split()).lower()
