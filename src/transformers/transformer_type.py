import enum

from .loc.concepts_transformer import LibraryOfCongressConceptsTransformer
from .loc.names_transformer import LibraryOfCongressNamesTransformer
from .loc.locations_transformer import LibraryOfCongressLocationsTransformer

LOC_SUBJECT_HEADINGS_URL = (
    "https://id.loc.gov/download/authorities/subjects.skosrdf.jsonld.gz"
)
LOC_NAMES_URL = "https://id.loc.gov/download/authorities/names.skosrdf.jsonld.gz"

GRAPH_QUERIES_SNS_TOPIC_ARN = (
    "arn:aws:sns:eu-west-1:760097843905:catalogue_graph_queries"
)


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
