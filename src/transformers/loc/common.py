ID_PREFIXES_TO_REMOVE = (
    "/authorities/subjects/",
    "http://id.loc.gov/authorities/subjects/",
    "/authorities/names/",
    "http://id.loc.gov/authorities/names/",
)


def remove_id_prefix(raw_id: str) -> str:
    for prefix in ID_PREFIXES_TO_REMOVE:
        raw_id = raw_id.removeprefix(prefix)
    return raw_id


class RawLibraryOfCongressConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept

    @property
    def source_id(self) -> str:
        return remove_id_prefix(self.raw_concept["@id"])
