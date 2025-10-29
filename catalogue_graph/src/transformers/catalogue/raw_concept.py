import re
from typing import cast

from models.pipeline.concept import (
    IdentifiedConcept,
)
from models.pipeline.identifier import (
    SourceIdentifier,
)
from utils.types import ConceptSource

from .id_label_checker import IdLabelChecker


class RawCatalogueConcept:
    def __init__(
        self,
        extracted: IdentifiedConcept,
        id_label_checker: IdLabelChecker | None = None,
    ):
        self.raw_concept = extracted
        self.id_label_checker = id_label_checker

        self.wellcome_id = self.raw_concept.id.canonical_id
        self.label = self.raw_concept.label
        self.type = self.raw_concept.display_type

    @property
    def source_identifier(self) -> SourceIdentifier:
        """Returns metadata about the source identifier."""
        return self.raw_concept.id.source_identifier

    @property
    def source(self) -> ConceptSource:
        """Returns the concept source (one of "lc-names", "label-derived", etc.)."""
        identifier_type = self.source_identifier.identifier_type.id
        return cast(ConceptSource, identifier_type)

    @property
    def mesh_qualifier(self) -> str | None:
        """Returns MeSH qualifier ID, if present."""
        if self.source == "nlm-mesh":
            qualifier = re.search(r"Q\d+", self.source_identifier.value)
            if qualifier is not None:
                return qualifier.group()

        return None

    @property
    def source_concept_id(self) -> str:
        """Returns ID of source concept, if present."""
        source_id = self.source_identifier.value
        if isinstance(self.mesh_qualifier, str):
            source_id = source_id.replace(self.mesh_qualifier, "")

        return source_id

    @property
    def label_matched_source_concept_id(self) -> str | None:
        assert self.id_label_checker is not None

        matched_id = self.id_label_checker.get_id(self.label, self.type)
        return matched_id

    @property
    def has_valid_source_concept(self) -> bool:
        """Checks if the source concept ID format matches the specified source."""
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
