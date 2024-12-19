class RawLibraryOfCongressConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept
        self._raw_concept_node = self._extract_concept_node()

    @staticmethod
    def _remove_id_prefix(raw_id: str):
        prefixes_to_remove = [
            "/authorities/subjects/",
            "http://id.loc.gov/authorities/subjects/",
            "/authorities/names/",
        ]

        for prefix in prefixes_to_remove:
            raw_id = raw_id.removeprefix(prefix)

        return raw_id

    def _extract_concept_node(self):
        graph = self.raw_concept["@graph"]
        concept_nodes = [
            node
            for node in graph
            if self.source_id in node.get("@id") and node["@type"] == "skos:Concept"
        ]

        # Some LoC concepts (e.g. deprecated concepts) do not store a concept node in their graph.
        # When this happens, return `None` because there is no concept for us to extract.
        if len(concept_nodes) == 0:
            return None

        return concept_nodes[0]

    @staticmethod
    def _extract_label(raw_label: str | dict):
        # Labels are either stored directly as strings, or as nested JSON objects with a `@value` property.
        if isinstance(raw_label, str):
            return raw_label
        # Very rarely, labels are returned as a list of strings. When this happens,
        # we only return the first item in the list.
        if isinstance(raw_label, list):
            return raw_label[0]

        return raw_label["@value"]

    def exclude(self):
        if self._raw_concept_node is None:
            return True

        # Remove concepts whose IDs have the "-781" suffix. They are duplicates of concepts with non-suffixed IDs.
        # The suffix represents the fact that the concept in question is part of the LCSH - Geographic collection.
        if self.source_id.endswith("-781"):
            return True

        return False

    @property
    def source_id(self):
        return self._remove_id_prefix(self.raw_concept["@id"])

    @property
    def label(self):
        raw_preferred_label = self._raw_concept_node["skos:prefLabel"]
        return self._extract_label(raw_preferred_label)

    @property
    def alternative_labels(self):
        raw_alternative_labels = self._raw_concept_node.get("skos:altLabel", [])

        # Raw alternative labels are either returned in a list of labels, or as a single label
        # in the same format as `skos:prefLabel`
        if isinstance(raw_alternative_labels, list):
            return [self._extract_label(item) for item in raw_alternative_labels]

        return [self._extract_label(raw_alternative_labels)]

    @property
    def broader_concept_ids(self):
        broader_concepts = self._raw_concept_node.get("skos:broader", [])

        # Sometimes broader concepts are returned as a list of concepts, and sometimes as just a single JSON
        if isinstance(broader_concepts, dict):
            broader_concepts = [broader_concepts]

        broader_ids = []
        for concept in broader_concepts:
            # Some broader concepts have IDs in the format `_:n<some_hexadecimal_string>`.
            # These IDs do not exist in the LoC source files or the LoC website, so we filter them out.
            if concept["@id"].startswith("_:n"):
                continue

            broader_ids.append(self._remove_id_prefix(concept["@id"]))

        return broader_ids

    @property
    def is_geographic(self):
        if self._raw_concept_node is None:
            return False

        # Notations are sometimes returned as a single notation (with a `@type` property, and a `@value` property),
        # and sometimes as a list of notations.
        notation = self._raw_concept_node.get("skos:notation", [])
        if isinstance(notation, dict):
            notation = [notation]

        notation_types = {item.get("@type") for item in notation}
        return "http://id.loc.gov/datatypes/codes/gac" in notation_types

    @property
    def source(self):
        if "subjects" in self.raw_concept["@id"]:
            return "lc-subjects"

        if "names" in self.raw_concept["@id"]:
            return "lc-names"

        raise ValueError("Unknown concept type.")
