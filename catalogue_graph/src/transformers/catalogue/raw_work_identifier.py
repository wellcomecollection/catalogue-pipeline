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

        # We don't need to capture parent/child relationships between Calm altref identifiers since Calm ref
        # identifiers capture the same hierarchy.
        if self.identifier_type == "calm-altref-no":
            return None

        path_fragments = self.path.split("/")

        # In most cases, the identifier value represents the full hierarchy of ancestors, matching the collection path.
        # To get the parent identifier, take everything before the last slash (e.g. PPMIA/A/6/2/21/5 => PPMIA/A/6/2/21).
        # Calm ref identifiers match this pattern.
        if self.identifier == self.path:
            return self._make_unique_id("/".join(path_fragments[:-1]))
        
        # In some cases, each 'fragment' of the collection path represents an identifier. The current (child) identifier
        # either equals the full last fragment (e.g. grandparent/parent/child), or the last part of the last fragment,
        # where individual parts ('subfragments') are separated by underscores (e.g. parent/something_child). 
        last_fragment = path_fragments[-1]
        last_partial_fragment = None
        if "_" in last_fragment:
            last_partial_fragment = last_fragment.split("_")[-1]

        # The parent identifier is stored in the second to last fragment (e.g. grandparent/parent/child => parent).
        # Sierra iconographic numbers and Tei manuscript identifiers match this pattern.
        if self.identifier in (last_fragment, last_partial_fragment):
            return self._make_unique_id(path_fragments[-2])

        return None
