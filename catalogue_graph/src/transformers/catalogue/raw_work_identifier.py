class RawCatalogueWorkIdentifier:
    def __init__(self, raw_identifier: dict, collection_path: str):
        self.raw_identifier = raw_identifier
        self.path = collection_path
    
    def _make_unique_id(self, identifier: str):
        return f"{self.identifier_type}||{identifier}"
    
    @property
    def unique_id(self) -> str:
        return self._make_unique_id(self.identifier)        

    @property
    def identifier(self) -> str:
        return self.raw_identifier["value"]

    @property
    def identifier_type(self) -> str:
        return self.raw_identifier["identifierType"]["id"]

    @property
    def parent(self) -> str | None:
        if self.path is None or "/" not in self.path:
            return
        
        path_fragments = self.path.split("/")
        
        last_partial_fragment = None
        if "_" in path_fragments[-1]:
            last_partial_fragment = path_fragments[-1].split("_")[-1]

        if self.identifier in (self.path, last_partial_fragment):                
            return self._make_unique_id("/".join(path_fragments[:-1]))
        if self.identifier in path_fragments[1:] and not self.path.startswith("(WCat)"):
            parent_index = path_fragments.index(self.identifier) - 1
            return self._make_unique_id(path_fragments[parent_index])
