from transformers.loc.common import RawLibraryOfCongressConcept


class RawLibraryOfCongressMADSConcept(RawLibraryOfCongressConcept):
    def __init__(self, raw_concept: dict):
        super().__init__(raw_concept)
