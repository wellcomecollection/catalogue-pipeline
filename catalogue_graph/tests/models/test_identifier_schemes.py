"""
Checks the Python scheme registry against the Scala internal model.

A scheme emitted by a Python transformer but missing from IdentifierType.scala
fails in the merger with NoSuchElementException (mimsy-reference,
platform#6620), so every registry scheme carried through the Scala stages must
exist there. The reverse is fine: Scala may know schemes we do not list.
"""

import re
from pathlib import Path

from models.identifier_schemes import all_schemes

REPO_ROOT = Path(__file__).resolve().parents[3]
SCALA_IDENTIFIER_TYPE_PATH = (
    REPO_ROOT
    / "common/internal_model/src/main/scala/weco/catalogue/internal_model/identifiers/IdentifierType.scala"
)


def scala_scheme_ids() -> set[str]:
    return set(
        re.findall(
            r'val id = "([^"]+)"',
            SCALA_IDENTIFIER_TYPE_PATH.read_text(encoding="utf-8"),
        )
    )


def test_scala_extraction_finds_schemes() -> None:
    # Guards the regex and path against IdentifierType.scala moving or changing shape
    assert len(scala_scheme_ids()) > 10


def test_every_registry_scheme_exists_in_scala_model() -> None:
    registry_ids = {s.id for s in all_schemes() if s.in_scala_model}
    missing = registry_ids - scala_scheme_ids()
    assert not missing, (
        f"Schemes in the Python registry but not in IdentifierType.scala: "
        f"{sorted(missing)}. Works carrying them will fail to decode in the merger."
    )


def test_graph_only_schemes_are_really_absent_from_scala() -> None:
    # If Scala learns one of these, drop its in_scala_model=False flag
    graph_only_ids = {s.id for s in all_schemes() if not s.in_scala_model}
    assert not graph_only_ids & scala_scheme_ids()
