from models.graph_node import WorkType
from sources.catalogue.concepts_source import CONCEPT_KEYS

from .raw_concept import RawCatalogueConcept


class RawCatalogueWork:
    def __init__(self, raw_work: dict):
        self.raw_work = raw_work

    @property
    def wellcome_id(self) -> str:
        wellcome_id = self.raw_work["id"]
        assert isinstance(wellcome_id, str)
        return wellcome_id

    @property
    def label(self) -> str:
        label = self.raw_work["title"]
        assert isinstance(label, str)
        return label

    @property
    def type(self) -> WorkType:
        concept_type: WorkType = self.raw_work["type"]
        return concept_type

    @property
    def alternative_labels(self) -> list[str]:
        alternative_titles = self.raw_work["alternativeTitles"]
        assert isinstance(alternative_titles, list)
        return alternative_titles
    
    @property
    def concept_ids(self) -> list[str]:
        concept_ids = []
        for concept_key in CONCEPT_KEYS:
            for concept in self.raw_work.get(concept_key, []):
                raw_concept = RawCatalogueConcept(concept)
                
                if raw_concept.is_concept:
                    concept_ids.append(raw_concept.wellcome_id)
            
        return concept_ids

