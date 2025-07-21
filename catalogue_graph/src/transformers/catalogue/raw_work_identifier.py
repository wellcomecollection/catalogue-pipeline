from sources.catalogue.work_identifiers_source import RawDenormalisedWorkIdentifier


class RawCatalogueWorkIdentifier:
    def __init__(self, raw_identifier: RawDenormalisedWorkIdentifier):
        self.raw_identifier = raw_identifier.identifier
        self.path = raw_identifier.work_collection_path

    def _make_unique_id(self, identifier: str) -> str:
        return f"{self.identifier_type}||{identifier}"

    @property
    def unique_id(self) -> str:
        return self._make_unique_id(self.identifier)

    @property
    def identifier(self) -> str:
        identifier = self.raw_identifier["value"]
        assert isinstance(identifier, str)
        return identifier

    @property
    def identifier_type(self) -> str:
        identifier_type = self.raw_identifier["identifierType"]["id"]
        assert isinstance(identifier_type, str)
        return identifier_type

    @property
    def parent(self) -> str | None:
        if self.path is None or "/" not in self.path:
            return None

        path_fragments = self.path.split("/")

        last_fragment = path_fragments[-1]
        last_partial_fragment = None
        if "_" in last_fragment:
            last_partial_fragment = last_fragment.split("_")[-1]
        
        if self.identifier == self.path:
            return self._make_unique_id("/".join(path_fragments[:-1]))

        # and not self.path.startswith("(WCat)")
        if self.identifier in (last_fragment, last_partial_fragment):
            return self._make_unique_id(path_fragments[-2])

        return None
