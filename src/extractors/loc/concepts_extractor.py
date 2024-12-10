from collections.abc import Generator
from models.graph_node import SourceConcept
from models.graph_edge import SourceConceptNarrowerThan, BaseEdge
from sources.gzip import GZipSource


class RawLibraryOfCongressConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept
        self.source_id = self._extract_source_id()
        self.raw_concept_node = self._extract_concept_node()

    @staticmethod
    def remove_id_prefix(raw_id: str):
        return raw_id.removeprefix("/authorities/subjects/").removeprefix(
            "http://id.loc.gov/authorities/subjects/"
        )

    def _extract_source_id(self):
        return self.remove_id_prefix(self.raw_concept["@id"])

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

        return raw_label["@value"]

    def _extract_preferred_label(self):
        raw_preferred_label = self.raw_concept_node["skos:prefLabel"]
        return self._extract_label(raw_preferred_label)

    def _extract_alternative_labels(self):
        raw_alternative_labels = self.raw_concept_node.get("skos:altLabel", [])

        # Raw alternative labels are either returned in a list of labels, or as a single label
        # in the same format as `skos:prefLabel`
        if isinstance(raw_alternative_labels, list):
            return [self._extract_label(item) for item in raw_alternative_labels]

        return [self._extract_label(raw_alternative_labels)]

    def _extract_broader_concepts(self):
        broader_concepts = self.raw_concept_node.get("skos:broader", [])

        # Sometimes broader concepts are returned as a list of concepts, and sometimes as just a single JSON
        if isinstance(broader_concepts, dict):
            broader_concepts = [broader_concepts]

        return broader_concepts

    def extract_edges(self) -> list[BaseEdge]:
        if self.raw_concept_node is None:
            return []

        broader_concepts = self._extract_broader_concepts()
        broader_ids = [
            self.remove_id_prefix(concept["@id"]) for concept in broader_concepts
        ]

        edges = []
        for broader_id in broader_ids:
            edges.append(
                SourceConceptNarrowerThan(from_id=self.source_id, to_id=broader_id)
            )

        return edges

    def transform_to_source_concept(self):
        """Transforms the raw LoC concept into a SourceConcept"""
        if self.raw_concept_node is None:
            return None

        label = self._extract_preferred_label()
        alternative_labels = self._extract_alternative_labels()

        return SourceConcept(
            id=self.source_id,
            label=label,
            source="lc-subjects",
            alternative_ids=[],
            alternative_labels=alternative_labels,
            description=None,
        )


class LibraryOfCongressConceptsExtractor:
    def __init__(self, url: str):
        self.source = GZipSource(url)

    def extract_nodes(self, number: int = None) -> Generator[SourceConcept]:
        """
        Extracts and returns SourceConcept nodes from LoC Subject Headings.
        Takes an optional parameter to only extract the first `number` nodes.
        """
        counter = 0

        for raw_concept in self.source.stream_raw():
            source_concept = RawLibraryOfCongressConcept(
                raw_concept
            ).transform_to_source_concept()

            if source_concept:
                yield source_concept

            counter += 1
            if counter == number:
                return

    def extract_edges(self, number: int = None) -> Generator[BaseEdge]:
        """
        Extracts and returns SourceConceptNarrowerThan edges from LoC Subject Headings.
        Takes an optional parameter to only extract the first `number` edges.
        """
        counter = 0

        for raw_concept in self.source.stream_raw():
            edges = RawLibraryOfCongressConcept(raw_concept).extract_edges()
            for edge in edges:
                counter += 1
                yield edge

                if counter == number:
                    return
