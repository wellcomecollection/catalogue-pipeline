import re

from utils.types import ConceptSource, ConceptType, WorkConceptKey

from .id_label_checker import IdLabelChecker


class RawCatalogueConcept:
    def __init__(
        self,
        raw_data: tuple[dict, WorkConceptKey],
        id_label_checker: IdLabelChecker | None = None,
    ):
        self.raw_concept = raw_data[0]
        self.referenced_in = raw_data[1]
        self.id_label_checker = id_label_checker

    @property
    def is_concept(self) -> bool:
        """
        Determines whether a given block of JSON represents a Concept as returned from the Catalogue API.
        A Concept is a block of JSON with a type property and a list of identifiers.
        """
        return self.raw_concept.get("id", {}).get("canonicalId") is not None

    @property
    def wellcome_id(self) -> str:
        """Returns the canonical Wellcome identifier."""
        wellcome_id: str = self.raw_concept["id"]["canonicalId"]
        return wellcome_id

    @property
    def label(self) -> str:
        """Returns the concept label."""
        label: str = self.raw_concept["label"]
        return label

    @property
    def type(self) -> ConceptType:
        """Returns the concept type (one of "Person", "Concept", "Genre", etc.)."""

        # All root concepts extracted from the 'subjects' section are of type 'Subject'. However, these concepts do
        # not have a 'type' field in the denormalised index, so we need to add it here.
        if self.referenced_in == "subjects":
            return "Subject"
        # All concepts extracted from the 'genres' section are always of type 'Genre' (but the denormalised index uses
        # the term 'GenreConcept').
        if self.referenced_in == "genres":
            return "Genre"

        concept_type: ConceptType = self.raw_concept["type"]
        return concept_type

    @property
    def raw_identifier(self) -> dict:
        """Returns metadata about the source identifier."""
        raw_identifier = self.raw_concept["id"]["sourceIdentifier"]
        assert isinstance(raw_identifier, dict)
        return raw_identifier

    @property
    def source(self) -> ConceptSource:
        """Returns the concept source (one of "lc-names", "label-derived", etc.)."""
        source: ConceptSource = self.raw_identifier["identifierType"]["id"]
        return source

    @property
    def mesh_qualifier(self) -> str | None:
        """Returns MeSH qualifier ID, if present."""
        if self.source == "nlm-mesh":
            qualifier = re.search(r"Q\d+", self.raw_identifier.get("value", ""))
            if qualifier is not None:
                return qualifier.group()

        return None

    @property
    def source_concept_id(self) -> str | None:
        """Returns ID of source concept, if present."""
        source_id = self.raw_identifier.get("value")
        if isinstance(source_id, str):
            if isinstance(self.mesh_qualifier, str):
                source_id = source_id.replace(self.mesh_qualifier, "")
            return source_id

        return None

    @property
    def label_matched_source_concept_id(self) -> str | None:
        assert self.id_label_checker is not None

        matched_id = self.id_label_checker.get_id(self.label, self.type)
        return matched_id

    @property
    def has_valid_source_concept(self) -> bool:
        """Checks if the source concept ID format matches the specified source."""
        if not isinstance(self.source_concept_id, str):
            return False

        assert self.id_label_checker is not None

        # For MeSH, we not only require that the source identifier has a corresponding node in the graph,
        # but also that the label of the node matches the label of the catalogue concept
        if self.source == "nlm-mesh":
            source_label = self.id_label_checker.get_label(
                self.source_concept_id, self.source
            )
            source_alternative_labels = self.id_label_checker.get_alternative_labels(
                self.source_concept_id, self.source
            )

            all_source_labels = source_alternative_labels
            if source_label is not None:
                all_source_labels.append(source_label)

            normalised_label = self.label.lower()

            return any(label in normalised_label for label in all_source_labels)

        # For LoC, we only require that the referenced source identifier exists in the graph.
        if self.source in ("lc-subjects", "lc-names"):
            return (
                self.id_label_checker.get_label(self.source_concept_id, self.source)
                is not None
            )

        return False
