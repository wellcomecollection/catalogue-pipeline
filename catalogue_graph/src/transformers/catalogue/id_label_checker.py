from collections import defaultdict
from itertools import product

from models.graph_node import ConceptSource, ConceptType
from utils.aws import NodeType, OntologyType, fetch_transformer_output_from_s3

SOURCES_BY_PRIORITY = ['nlm-mesh', 'lc-subjects', 'lc-names']
AMBIGUITY_THRESHOLD = 3

BLACKLISTED_CONCEPT_LABELS = {'siamese', 'consumption'}


class IdLabelChecker:
    """
    A bidirectional dictionary for checking catalogue concepts against data from source ontologies.
    """
    ids_to_labels = defaultdict(lambda: defaultdict(str))
    ids_to_alternative_labels = defaultdict(lambda: defaultdict(list))

    labels_to_ids = defaultdict(lambda: defaultdict(list))
    alternative_labels_to_ids = defaultdict(lambda: defaultdict(list))

    def __init__(self, node_type: NodeType | list[NodeType], source: OntologyType | list[OntologyType]):
        if not isinstance(node_type, list):
            node_type = [node_type]
        if not isinstance(source, list):
            source = [source]

        for nt, s in product(node_type, source):
            for row in fetch_transformer_output_from_s3(nt, s):
                source_id = row[":ID"]
                label = row["label:String"].lower()
                alternative_labels = [
                    label.lower()
                    for label in row["alternative_labels:String"].split("||")
                    if label != ""
                ]
                
                if source_id[0] == 'n':
                    ontology = "lc-names"
                elif source_id[0] == 's':
                    ontology = "lc-subjects"
                elif source_id[0] == "D":
                    ontology = "nlm-mesh"
                else:
                    raise ValueError(f"Unexpected source id {source_id}")

                self.ids_to_labels[ontology][source_id] = label
                self.ids_to_alternative_labels[ontology][source_id] = alternative_labels

                self.labels_to_ids[ontology][label].append(source_id)
                for label in alternative_labels: 
                    self.alternative_labels_to_ids[ontology][label].append(source_id)                
    
    def get_id(self, label: str, concept_type: ConceptType) -> str | None:
        if label in BLACKLISTED_CONCEPT_LABELS:
            return None

        # First, try to match the concept label to a 'main' source concept label, in order of priority.
        for source in SOURCES_BY_PRIORITY:
            if len(source_ids := self.labels_to_ids[source][label]) > 0:
                # TODO: Some LoC subjects have duplicates (e.g. sh99001240 and sh85101195)
                return source_ids[0]

        # If no matches found, try matching on alternative labels
        for source in SOURCES_BY_PRIORITY:
            if len(source_ids := self.alternative_labels_to_ids[source][label]) > 0:
                # If a label matches more the alternative labels of more than 'AMBIGUITY_THRESHOLD' concepts
                # from any given source ontology, it's too ambiguous, and we shouldn't match it.
                if len(source_ids) > AMBIGUITY_THRESHOLD:
                    print(f"Ambiguous label '{label}' matches {len(source_ids)} alternative labels.")
                    return None
                
                # # Try not to match people/organisations to things
                # if concept_type in ("Person", "Agent", "Organisation") and source in ("nlm-mesh", "lc-subjects"):
                #     print(f"Not matching agent {label} to {source_ids}")
                #     continue
                # 
                # # Try not to match things to people/organisations
                # if concept_type not in ("Person", "Agent", "Organisation") and source == 'lc-names':
                #     print(f"Not matching thing {label} to {source_ids}")
                #     continue   

                return source_ids[0]   
    
    def get_label(self, source_id: str, source: ConceptSource) -> str | None:
        return self.ids_to_labels[source][source_id]

    def get_alternative_labels(self, source_id: str, source: ConceptSource) -> list[str] | None:
        return self.ids_to_alternative_labels[source][source_id]
