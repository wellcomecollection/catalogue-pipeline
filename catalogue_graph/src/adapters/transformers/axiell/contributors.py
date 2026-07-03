from pymarc.record import Record

from adapters.transformers.ebsco.label_subdivisions import build_concept
from adapters.transformers.marc.common import non_empty_subfields
from models.pipeline.concept import Contributor


def extract_contributor_names(record: Record) -> list[str]:
    return non_empty_subfields("720", "a", record)


def extract_contributors(record: Record) -> list[Contributor]:
    names = extract_contributor_names(record)
    return [Contributor(agent=build_concept(name, "Agent")) for name in names]
