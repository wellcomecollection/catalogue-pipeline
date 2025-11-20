from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.transformers.raw_concept import (
    DISPLAY_SOURCE_PRIORITY,
    get_priority_label,
)
from models.pipeline.concept import Concept


class WorkBaseTransformer:
    def __init__(self, extracted: VisibleExtractedWork):
        self.neptune_concepts = {c.concept.id: c for c in extracted.concepts}

    def get_standard_concept_label(self, concept: Concept) -> str:
        standard_label = concept.label
        if concept.id.canonical_id in self.neptune_concepts:
            extracted = self.neptune_concepts[concept.id.canonical_id]
            standard_label, _ = get_priority_label(extracted, DISPLAY_SOURCE_PRIORITY)

        return standard_label
