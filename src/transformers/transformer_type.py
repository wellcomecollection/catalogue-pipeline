import enum

from .loc.concepts_transformer import LibraryOfCongressConceptsTransformer
from .loc.locations_transformer import LibraryOfCongressLocationsTransformer
from .loc.names_transformer import LibraryOfCongressNamesTransformer

LOC_SUBJECT_HEADINGS_URL = (
    "https://id.loc.gov/download/authorities/subjects.skosrdf.jsonld.gz"
)
LOC_NAMES_URL = "https://id.loc.gov/download/authorities/names.skosrdf.jsonld.gz"


class TransformerType(enum.Enum):
    LOC_CONCEPTS = LibraryOfCongressConceptsTransformer(LOC_SUBJECT_HEADINGS_URL)
    LOC_NAMES = LibraryOfCongressNamesTransformer(LOC_NAMES_URL)
    LOC_LOCATIONS = LibraryOfCongressLocationsTransformer(
        LOC_SUBJECT_HEADINGS_URL, LOC_NAMES_URL
    )

    def __str__(self):
        return self.name.lower()

    # For parsing lowercase Lambda/command line arguments
    @staticmethod
    def argparse(s):
        return TransformerType[s.upper()]
