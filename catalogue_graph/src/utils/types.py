from typing import Literal

NodeType = Literal["concepts", "names", "locations"]
OntologyType = Literal[
    "mesh", "loc", "wikidata_linked_mesh", "wikidata_linked_loc", "wikidata"
]

WorkConceptKey = Literal["subjects", "genres", "contributors"]

WorkIdentifiersKey = Literal["sourceIdentifier", "otherIdentifiers"]

# Catalogue concepts have a specific type and source
# This list should be kept in sync with the one defined in
# `pipeline/id_minter/src/main/scala/weco/pipeline/id_minter/steps/IdentifierGenerator.scala`
ConceptType = Literal[
    "Person",
    "Concept",
    "Organisation",
    "Place",
    "Agent",
    "Meeting",
    "Genre",
    "Period",
    "Subject",
]

ConceptSource = Literal[
    "label-derived", "nlm-mesh", "lc-subjects", "lc-names", "viaf", "fihrist"
]

WorkType = Literal["Work", "Series", "Section", "Collection"]

IngestorType = Literal["concepts"]
IngestorLoadFormat = Literal["parquet", "jsonl"]
