from models.pipeline.concept import Concept

from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.transformers.concept_override import ConceptTextOverrideProvider


class WorkBaseTransformer:
    def __init__(self, extracted: VisibleExtractedWork):
        self.neptune_concepts = {c.concept.id: c for c in extracted.concepts}
        self.label_override_provider = ConceptTextOverrideProvider()

    def get_standard_concept_label(self, concept: Concept) -> str:
        return concept.label
