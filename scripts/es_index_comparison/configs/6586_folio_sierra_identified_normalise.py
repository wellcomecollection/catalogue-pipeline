"""Normaliser for platform#6586: Sierra works in production against the FOLIO works replacing them.

Each rule removes a difference that is expected between the Scala Sierra
transformer and the Python FOLIO transformer.
"""

import unicodedata

OWN_IDENTIFIER_TYPES = {
    "sierra-system-number",
    "sierra-identifier",
    "folio-instance",
    "folio-instance-hrid",
}
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
    if sierra["type"] != "Visible" or folio["type"] != "Visible":
        return None

    for work in (sierra, folio):
        fill_missing_lists(work)
        work["data"] = clean_text(work["data"])
        work["state"]["relations"] = clean_text(work["state"]["relations"])
        work["data"]["otherIdentifiers"] = [
            identifier
            for identifier in work["data"]["otherIdentifiers"]
            if identifier["identifierType"]["id"] not in OWN_IDENTIFIER_TYPES
        ]
        work["state"]["mergeCandidates"] = merge_candidate_keys(work)

    normalise_sierra(sierra)
    normalise_folio(sierra, folio)
    return drop_empty(sierra), drop_empty(folio)


def clean_text(value):
    """Return a copy with every string in NFC, backslashes as spaces and whitespace collapsed.

    Sierra stores a mix of Unicode forms, the Scala transformer does not trim,
    and the migration to FOLIO turned backslashes into spaces.
    """
    if isinstance(value, dict):
        return {key: clean_text(item) for key, item in value.items()}
    if isinstance(value, list):
        return [clean_text(item) for item in value]
    if isinstance(value, str):
        return " ".join(unicodedata.normalize("NFC", value).replace("\\", " ").split())
    return value


def fill_missing_lists(work: dict) -> None:
    for name in LIST_FIELDS:
        work["data"][name] = work["data"].get(name) or []
    relations = work["state"].get("relations") or {}
    relations["ancestors"] = relations.get("ancestors") or []
    work["state"]["relations"] = relations


def merge_candidate_keys(work: dict) -> list[str]:
    """Reduce merge candidates to their source identifiers, leaving out FOLIO HRID candidates."""
    keys = []
    for candidate in work["state"].get("mergeCandidates") or []:
        identifier = candidate["id"]["sourceIdentifier"]
        if identifier["identifierType"]["id"] != "folio-instance-hrid":
            keys.append(f"{identifier['identifierType']['id']}/{identifier['value']}")
    return keys


def normalise_sierra(sierra: dict) -> None:
    data = sierra["data"]

    # The Python transformer trims every trailing comma.
    for contributor in data["contributors"]:
        contributor["agent"]["label"] = contributor["agent"]["label"].rstrip(",")

    for genre in data["genres"]:
        for concept in genre["concepts"]:
            if concept["label"] == "Electronic Books":
                concept["label"] = "Electronic books"

    # The Python transformer always stores a type on concepts.
    for production in data["production"]:
        for place in production.get("places") or []:
            place["type"] = "Place"
        if production.get("function"):
            production["function"]["type"] = "Concept"

    # The Python period parser starts an open range at year 1.
    for period in periods(sierra):
        date_range = period.get("range")
        if date_range and date_range["from"] == "-9999-01-01T00:00:00Z":
            date_range["from"] = "0001-01-01T00:00:00Z"

    # The Python transformer strips the organisation prefix from a host identifier.
    if data.get("collectionPath"):
        path = data["collectionPath"]["path"]
        data["collectionPath"]["path"] = path.removeprefix("(UkLW)")

    # Works no longer store siblings or children.
    for name in ("siblingsPreceding", "siblingsSucceeding", "children"):
        sierra["state"]["relations"].pop(name, None)


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
    # The Scala transformer leaves these types out.
    for subject in folio["data"]["subjects"]:
        subject.pop("type", None)
    for production in folio["data"]["production"]:
        for date in production.get("dates") or []:
            date.pop("type", None)

    # The Python period parser handles more patterns, so a date range the Sierra
    # concept lacks is not a difference.
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


def drop_empty(value):
    """Drop null and empty dictionary members, so that a missing key equals an empty one."""
    if isinstance(value, dict):
        members = {key: drop_empty(item) for key, item in value.items()}
        return {key: item for key, item in members.items() if item not in (None, [], {})}
    if isinstance(value, list):
        return [drop_empty(item) for item in value]
    return value
