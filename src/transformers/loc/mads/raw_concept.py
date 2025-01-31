from transformers.loc.common import RawLibraryOfCongressConcept


class RawLibraryOfCongressMADSConcept(RawLibraryOfCongressConcept):
    def __init__(self, raw_concept: dict):
        super().__init__(raw_concept)

    def _extract_concept_node(self) -> dict | None:
        graph: list[dict] = self.raw_concept.get("@graph", [])
        for node in graph:
            # madsrdf:Authority corresponds to the "idea or notion"
            # So the node we are after is the one whose id matches, and is an Authority
            # Ignore DeprecatedAuthority in this context, as they are to be excluded.
            # https://www.loc.gov/standards/mads/rdf/#t21
            if (
                self.source_id in node.get("@id", "")
                and "madsrdf:Authority" in node["@type"]
            ):
                return node
        return None
