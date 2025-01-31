import json

from test_utils import load_fixture

from transformers.loc.mads.raw_concept import RawLibraryOfCongressMADSConcept

sh2010105253 = json.loads(load_fixture("mads_composite_concept.json"))
