from transformers.loc.common import RawLibraryOfCongressConcept, remove_id_prefix


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

    @property
    def label(self) -> str:
        assert self._raw_concept_node is not None
        raw_preferred_label = self._raw_concept_node["madsrdf:authoritativeLabel"]
        return self._extract_label(raw_preferred_label)

    @property
    def is_geographic(self) -> bool:
        assert self._raw_concept_node is not None
        """Returns True if the node represents a geographic concept, as determined by @type"""
        return "madsrdf:Geographic" in self._raw_concept_node.get("@type", [])

    @property
    def broader_concept_ids(self) -> list[str]:
        assert self._raw_concept_node is not None
        return _filter_irrelevant_ids(
            [
                remove_id_prefix(broader["@id"])
                for broader in _as_list(
                    self._raw_concept_node.get("madsrdf:hasBroaderAuthority", [])
                )
            ]
        )

    @property
    def narrower_concept_ids(self) -> list[str]:
        return self._narrowers_from_narrower_authority() + self._narrowers_from_component_list()


    def _narrowers_from_component_list(self):
        assert self._raw_concept_node is not None
        return _filter_irrelevant_ids(
            [
                remove_id_prefix(broader["@id"])
                for broader in _as_list(
                    self._raw_concept_node.get("madsrdf:componentList", {}).get("@list", [])
                )
            ]
        )

    def _narrowers_from_narrower_authority(self):
        assert self._raw_concept_node is not None
        return _filter_irrelevant_ids(
            [
                remove_id_prefix(broader["@id"])
                for broader in _as_list(
                    self._raw_concept_node.get("madsrdf:hasNarrowerAuthority", [])
                )
            ]
        )

    @property
    def related_concept_ids(self) -> list[str]:
        assert self._raw_concept_node is not None
        return _filter_irrelevant_ids(
            [
                remove_id_prefix(broader["@id"])
                for broader in _as_list(
                    self._raw_concept_node.get("madsrdf:hasReciprocalAuthority", [])
                )
            ]
        )


def _filter_irrelevant_ids(ids: list[str]) -> list[str]:
    return [concept_id for concept_id in ids if not concept_id.startswith("_:n")]


def _as_list(dict_or_list: dict | list[dict]) -> list[dict]:
    # Some fields in the source data may contain one or more values
    # When it contains multiple values, it will be a list,
    # but in the case where they contain just one value, it is not.
    # Wrap bare single values in a list, for consistency of processing downstream
    if isinstance(dict_or_list, dict):
        return [dict_or_list]
    return dict_or_list
