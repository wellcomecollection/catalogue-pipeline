from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.transformers.concept_override import ConceptTextOverrideProvider
from ingestor.transformers.raw_concept import (
    DISPLAY_SOURCE_PRIORITY,
    get_priority_label,
)
from models.pipeline.concept import Concept


class WorkBaseTransformer:
    def __init__(self, extracted: VisibleExtractedWork):
        self.neptune_concepts = {c.concept.id: c for c in extracted.concepts}
        self.label_override_provider = ConceptTextOverrideProvider()

    def get_standard_concept_label(self, concept: Concept) -> str:
        """Return the highest priority label for the given concept, as determined by the catalogue graph."""
        standard_label = concept.label
        if concept.id.canonical_id in self.neptune_concepts:
            extracted = self.neptune_concepts[concept.id.canonical_id]
            standard_label, _ = get_priority_label(extracted, DISPLAY_SOURCE_PRIORITY)

        label_override = None
        if concept.id.canonical_id:
            label_override = self.label_override_provider.get_label_override(
                concept.id.canonical_id
            )

        return label_override or standard_label
