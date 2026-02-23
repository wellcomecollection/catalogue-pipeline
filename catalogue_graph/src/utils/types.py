from typing import Literal, get_args

# Reason type Literal aliases & derived tuples (single source of truth for values).
Environment = Literal["prod", "dev"]

InvisibleReasonType = Literal[
    "CopyrightNotCleared",
    "SourceFieldMissing",
    "InvalidValueInSourceField",
    "UnlinkedHistoricalLibraryMiro",
    "UnableToTransform",
    "MetsWorksAreNotVisible",
    "MimsyWorksAreNotVisible",
]
INVISIBLE_REASON_TYPES: tuple[str, ...] = get_args(InvisibleReasonType)

DeletedReasonType = Literal[
    "DeletedFromSource",
    "SuppressedFromSource",
    "TeiDeletedInMerger",
]
DELETED_REASON_TYPES: tuple[str, ...] = get_args(DeletedReasonType)

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

# Catalogue concepts have a specific type and source.
"""Keep in sync with Scala IdentifierGenerator in
`pipeline/id_minter/src/main/scala/weco/pipeline/id_minter/steps/IdentifierGenerator.scala`.
"""
_BaseConceptType = Literal[
    "Person",
    "Concept",
    "Organisation",
    "Place",
    "Agent",
    "Meeting",
    "Period",
    "Subject",
]
ConceptType = Literal[_BaseConceptType, "Genre"]
RawConceptType = Literal[_BaseConceptType, "GenreConcept"]

ConceptSource = Literal[
    "label-derived",
    "nlm-mesh",
    "lc-subjects",
    "lc-names",
    "viaf",
    "fihrist",
    "wikidata",
    "weco-authority",
]

WorkType = Literal["Standard", "Series", "Section", "Collection"]
DisplayWorkType = Literal["Work", "Series", "Section", "Collection"]
WorkStatus = Literal["Visible", "Redirected", "Deleted", "Invisible"]

IngestorType = Literal["works", "concepts"]

IngestorLoadFormat = Literal["parquet", "jsonl"]

LocTransformerType = Literal["loc_concepts", "loc_names", "loc_locations"]
MeshTransformerType = Literal["mesh_concepts", "mesh_locations"]

WikidataLinkedLocTransformerType = Literal[
    "wikidata_linked_loc_concepts",
    "wikidata_linked_loc_locations",
    "wikidata_linked_loc_names",
]
WikidataLinkedMeshTransformerType = Literal[
    "wikidata_linked_mesh_concepts",
    "wikidata_linked_mesh_locations",
]
WikidataTransformerType = Literal[
    WikidataLinkedLocTransformerType, WikidataLinkedMeshTransformerType
]
CatalogueTransformerType = Literal[
    "catalogue_concepts", "catalogue_works", "catalogue_work_identifiers"
]
TransformerType = Literal[
    LocTransformerType,
    MeshTransformerType,
    WikidataTransformerType,
    CatalogueTransformerType,
    "weco_concepts",
]

FullGraphRemoverType = Literal[
    LocTransformerType, MeshTransformerType, WikidataTransformerType
]
GraphRemoverFolder = Literal["previous_ids_snapshot", "deleted_ids", "added_ids"]

EntityType = Literal["nodes", "edges"]
StreamDestination = Literal["s3", "local", "void"]
