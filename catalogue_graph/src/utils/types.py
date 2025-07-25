from typing import Literal

NodeType = Literal["concepts", "names", "locations"]
OntologyType = Literal[
    "mesh", "loc", "wikidata_linked_mesh", "wikidata_linked_loc", "wikidata"
]

WorkConceptKey = Literal["subjects", "genres", "contributors"]

WorkIdentifiersKey = Literal["sourceIdentifier", "otherIdentifiers"]
