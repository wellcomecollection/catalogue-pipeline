"""Normaliser for platform#6857: works merged by the Scala merger against works merged by the Python merger."""


def normalise(scala: dict, python: dict) -> tuple[dict, dict]:
    # Scala always writes relations, Python only when the work has ancestors.
    for work in (scala, python):
        relations = work["state"].get("relations") or {}
        if not relations.get("ancestors"):
            work["state"].pop("relations", None)
    return scala, python
