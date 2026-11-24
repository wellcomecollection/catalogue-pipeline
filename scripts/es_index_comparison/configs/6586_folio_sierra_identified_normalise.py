"""Normaliser for platform#6586: Sierra works in production against the FOLIO works replacing them.

Each rule removes a difference that is expected and accepted, so that it is not counted.
"""

import re
import unicodedata

# FOLIO works list their HRID, and Sierra works their seven-digit Sierra identifier.
OWN_RECORD_SCHEMES = {"folio-instance-hrid", "sierra-identifier"}
LIST_FIELDS = [
    "alternativeTitles",
    "contributors",
    "genres",
    "languages",
    "notes",
    "otherIdentifiers",
    "production",
    "subjects",
]


def normalise(sierra: dict, folio: dict) -> tuple[dict, dict] | None:
    # Two works of different types are compared on type alone.
    if sierra["type"] != folio["type"]:
        return {"type": sierra["type"]}, {"type": folio["type"]}

    # Only the Scala transformer keeps these on a suppressed record. They have no
    # effect, because the matcher drops every link to or from a Deleted work.
    if sierra["type"] == "Deleted":
        sierra["state"]["relations"] = None
        sierra["state"]["mergeCandidates"] = []

    # The deleted reason names the source system.
    if folio.get("deletedReason", {}).get("info") == "Folio":
        folio["deletedReason"]["info"] = "Sierra"

    sierra_number = sierra["state"]["sourceIdentifier"]["value"]
    for work in (sierra, folio):
        fill_missing_lists(work)
        work["data"] = clean_text(work["data"])
        work["state"]["relations"] = clean_text(work["state"]["relations"])
        # Only the Python transformer trims the start of a description.
        if work["data"].get("description"):
            work["data"]["description"] = re.sub(r"<p>\s+", "<p>", work["data"]["description"])
        work["data"]["otherIdentifiers"] = other_identifiers(work, sierra_number)
        work["state"]["mergeCandidates"] = merge_candidates(work)

    normalise_sierra(sierra)
    normalise_folio(sierra, folio)
    return sierra, folio


def clean_text(value):
    """Return a copy of a value with every string in it cleaned."""
    if isinstance(value, dict):
        return {key: clean_text(item) for key, item in value.items()}
    if isinstance(value, list):
        return [clean_text(item) for item in value]
    if isinstance(value, str):
        # Sierra mixes Unicode forms, Scala does not trim, and the migration
        # turned backslashes into spaces.
        return " ".join(unicodedata.normalize("NFC", value).replace("\\", " ").split())
    return value


def other_identifiers(work: dict, sierra_number: str) -> list[dict]:
    """Return a work's other identifiers without those of its own source record."""
    kept = []
    for identifier in work["data"]["otherIdentifiers"]:
        own_scheme = identifier["identifierType"]["id"] in OWN_RECORD_SCHEMES
        # A FOLIO work also lists the number of the Sierra work it replaces.
        if not own_scheme and identifier["value"] != sierra_number:
            kept.append(identifier)
    return kept


def fill_missing_lists(work: dict) -> None:
    """Give a work an empty list for each list field it lacks."""
    # A Deleted work has no data.
    data = work.setdefault("data", {})
    for name in LIST_FIELDS:
        data[name] = data.get(name) or []
    relations = work["state"].get("relations") or {}
    relations["ancestors"] = relations.get("ancestors") or []
    work["state"]["relations"] = relations


def merge_candidates(work: dict) -> list[dict]:
    """Return a work's merge candidates without the one a FOLIO work has for its own HRID."""
    kept = []
    for candidate in work["state"].get("mergeCandidates") or []:
        if candidate["id"]["sourceIdentifier"]["identifierType"]["id"] != "folio-instance-hrid":
            kept.append(candidate)
    return kept


def normalise_sierra(sierra: dict) -> None:
    data = sierra["data"]

    # The Python transformer trims every trailing comma.
    for contributor in data["contributors"]:
        contributor["agent"]["label"] = contributor["agent"]["label"].rstrip(",")

    # The two transformers capitalise this genre differently.
    for genre in data["genres"]:
        for concept in genre["concepts"]:
            if concept["label"] == "Electronic Books":
                concept["label"] = "Electronic books"

    # The Python transformer stores a type where the Scala one stores none.
    for subject in data["subjects"]:
        subject.setdefault("type", "Subject")
    for production in data["production"]:
        for place in production.get("places") or []:
            place.setdefault("type", "Place")
        for date in production.get("dates") or []:
            date.setdefault("type", "Period")
        if production.get("function"):
            production["function"].setdefault("type", "Concept")

    # The Python period parser starts an open range at year 1.
    for period in periods(sierra):
        date_range = period.get("range")
        if date_range and date_range["from"] == "-9999-01-01T00:00:00Z":
            date_range["from"] = "0001-01-01T00:00:00Z"

    # The Python transformer strips the organisation prefix from a host identifier.
    if data.get("collectionPath"):
        path = data["collectionPath"]["path"]
        data["collectionPath"]["path"] = path.removeprefix("(UkLW)")


def periods(work: dict) -> list[dict]:
    """Return every production date and every subject and genre concept of a work."""
    data = work["data"]
    dates = [date for production in data["production"] for date in production.get("dates") or []]
    concepts = [
        concept
        for name in ("subjects", "genres")
        for entity in data[name]
        for concept in entity["concepts"]
    ]
    return dates + concepts


def normalise_folio(sierra: dict, folio: dict) -> None:
    # The Python period parser reads more dates, so a range the Sierra concept
    # lacks is not a difference.
    for name in ("subjects", "genres"):
        sierra_concepts = {}
        for entity in sierra["data"][name]:
            for concept in entity["concepts"]:
                sierra_concepts[identity(entity), identity(concept)] = concept
        for entity in folio["data"][name]:
            for concept in entity["concepts"]:
                sierra_concept = sierra_concepts.get((identity(entity), identity(concept)))
                if sierra_concept is not None and sierra_concept.get("range") is None:
                    concept.pop("range", None)


def identity(entity: dict) -> tuple:
    identifier = entity.get("id", {}).get("sourceIdentifier")
    if identifier is None:
        return ("label", entity["label"])
    return (identifier["identifierType"]["id"], identifier["ontologyType"], identifier["value"])
