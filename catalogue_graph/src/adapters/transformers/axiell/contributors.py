from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields
from models.pipeline.concept import Concept, Contributor
from models.pipeline.identifier import Identifiable


def extract_contributor_names(record: Record) -> list[str]:
    return non_empty_subfields("720", "a", record)


def extract_contributors(record: Record) -> list[Contributor]:
    names = extract_contributor_names(record)
    return [
        Contributor(
            agent=Concept(
                id=Identifiable.identifier_from_text(name, "Agent"),
                label=name,
                type="Agent",
            )
        )
        for name in names
    ]
