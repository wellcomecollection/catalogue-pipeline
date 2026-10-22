from utils.types import ConceptType

# Prioritise concepts, with more specific ones (e.g. 'Person') above less specific ones (e.g. 'Agent').
# Sometimes a concept is classified under types which are mutually exclusive. For example, there are
# several hundred concepts categorised as both a 'Person' and an 'Organisation'. These inconsistencies arise
# upstream, and we cannot easily resolve them here. To mitigate this issue, the priority list below is ordered
# to maximise the probability of choosing the right type based on an analysis of current inconsistencies. (For
# example, when a concept is categorised as both an 'Organisation' and a 'Place', the 'Place' type is almost
# always the correct one, which is why 'Place' is higher in the priority list than 'Organisation').
CONCEPT_TYPES_BY_PRIORITY: list[ConceptType] = [
    "Genre",
    "Place",
    "Person",
    "Organisation",
    "Period",
    "Meeting",
    "Agent",
    "Subject",
    "Concept",
]


def get_most_specific_concept_type(concept_types: list[ConceptType]) -> ConceptType:
    # Concepts which are not connected to any Works will not have any types associated with them. We periodically
    # remove such concepts from the graph, but there might be a few of them at any given point.
    if len(concept_types) == 0:
        return "Concept"

    for concept_type in CONCEPT_TYPES_BY_PRIORITY:
        if concept_type in concept_types:
            return concept_type

    raise ValueError(f"Invalid set of concept types: {concept_types}.")
