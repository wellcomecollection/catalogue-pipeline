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
        work["data"]["otherIdentifiers"] = [
            identifier
            for identifier in work["data"]["otherIdentifiers"]
            if identifier["identifierType"]["id"] not in OWN_IDENTIFIER_TYPES
        ]
        work["state"]["mergeCandidates"] = merge_candidate_keys(work)
        for subject in work["data"]["subjects"]:
            subject["label"] = nfc(subject["label"])
            for concept in subject["concepts"]:
                concept["label"] = nfc(concept["label"])

    normalise_sierra(sierra)
    normalise_folio(sierra, folio)
    return drop_empty(sierra), drop_empty(folio)


def nfc(text: str) -> str:
    return unicodedata.normalize("NFC", text)


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

    # Sierra stores a mix of Unicode forms, and the Scala transformer does not trim.
    for name in ("title", "edition", "description", "lettering"):
        if data.get(name) is not None:
            data[name] = nfc(data[name])
    data["alternativeTitles"] = [nfc(title).strip() for title in data["alternativeTitles"]]
    if data.get("physicalDescription") is not None:
        data["physicalDescription"] = data["physicalDescription"].rstrip()
    for note in data["notes"]:
        note["contents"] = nfc(note["contents"])
    for contributor in data["contributors"]:
        contributor["agent"]["label"] = nfc(contributor["agent"]["label"])
    for ancestor in sierra["state"]["relations"]["ancestors"]:
        ancestor["title"] = nfc(ancestor["title"])

    for genre in data["genres"]:
        for concept in genre["concepts"]:
            if concept["label"] == "Electronic Books":
                concept["label"] = "Electronic books"

    for production in data["production"]:
        production["label"] = nfc(production["label"]).rstrip()
        # The Python transformer always stores a type on concepts.
        for place in production.get("places") or []:
            place["type"] = "Place"
            place["label"] = nfc(place["label"]).strip()
        if production.get("function"):
            production["function"]["type"] = "Concept"
        for agent in production.get("agents") or []:
            agent["label"] = nfc(agent["label"]).strip()
        for date in production.get("dates") or []:
            date_range = date.get("range")
            if date_range and date_range["from"] == "-9999-01-01T00:00:00Z":
                date_range["from"] = "0001-01-01T00:00:00Z"

    # Works no longer store siblings or children.
    for name in ("siblingsPreceding", "siblingsSucceeding", "children"):
        sierra["state"]["relations"].pop(name, None)


def normalise_folio(sierra: dict, folio: dict) -> None:
    # The Scala transformer leaves these types out.
    for subject in folio["data"]["subjects"]:
        subject.pop("type", None)
    for production in folio["data"]["production"]:
        for date in production.get("dates") or []:
            date.pop("type", None)

    # The Python period parser handles more patterns, so a date range the Sierra
    # concept lacks is not a difference.
    sierra_concepts = {
        (identity(subject), identity(concept)): concept
        for subject in sierra["data"]["subjects"]
        for concept in subject["concepts"]
    }
    for subject in folio["data"]["subjects"]:
        for concept in subject["concepts"]:
            sierra_concept = sierra_concepts.get((identity(subject), identity(concept)))
            if sierra_concept is not None and sierra_concept.get("range") is None:
                concept.pop("range", None)


def identity(entity: dict) -> tuple:
    identifier = entity["id"].get("sourceIdentifier")
    if identifier is None:
        return ("label", entity["label"])
    return (identifier["identifierType"]["id"], identifier["ontologyType"], identifier["value"])


def drop_empty(value):
    """Drop null and empty dictionary members, so that a missing key equals an empty one."""
    if isinstance(value, dict):
        members = {key: drop_empty(item) for key, item in value.items()}
        return {key: item for key, item in members.items() if item not in (None, [], {}, "")}
    if isinstance(value, list):
        return [drop_empty(item) for item in value]
    return value
