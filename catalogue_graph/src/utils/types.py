from typing import Literal

NodeType = Literal["concepts", "names", "locations"]
OntologyType = Literal[
    "mesh",
    "loc",
    "wikidata_linked_mesh",
    "wikidata_linked_loc",
    "wikidata",
    "catalogue",
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


LocTransformerType = Literal["loc_concepts", "loc_names", "loc_locations"]
MeshTransformerType = Literal["mesh_concepts", "mesh_locations"]
WikidataTransformerType = Literal[
    "wikidata_linked_loc_concepts",
    "wikidata_linked_loc_locations",
    "wikidata_linked_loc_names",
    "wikidata_linked_mesh_concepts",
    "wikidata_linked_mesh_locations",
]
CatalogueTransformerType = Literal[
    "catalogue_concepts", "catalogue_works", "catalogue_work_identifiers"
]
TransformerType = Literal[
    "loc_concepts",
    "loc_names",
    "loc_locations",
    "mesh_concepts",
    "mesh_locations",
    "wikidata_linked_loc_concepts",
    "wikidata_linked_loc_locations",
    "wikidata_linked_loc_names",
    "wikidata_linked_mesh_concepts",
    "wikidata_linked_mesh_locations",
    "catalogue_concepts",
    "catalogue_works",
    "catalogue_work_identifiers",
]


EntityType = Literal["nodes", "edges"]
StreamDestination = Literal["graph", "s3", "sns", "local", "void"]
