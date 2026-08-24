from pymarc.record import Record

from adapters.transformers.ebsco.label_subdivisions import build_concept
from adapters.transformers.marc.common import non_empty_subfields
from models.pipeline.concept import Subject


def extract_subject_labels(record: Record) -> list[str]:
    return non_empty_subfields("653", "a", record)


def extract_subjects(record: Record) -> list[Subject]:
    labels = extract_subject_labels(record)

    # Axiell marks LCSH-linked terms with a leading tag in the term text
    # ("<p>" originally, "(LCSH) " since the round 2 load; WEL-271 will move
    # this to an Authority field). Strip it so labels display clean and the
    # label-derived identifier matches the same term from other sources.
    labels = [
        label.removeprefix("<p>").removeprefix("(LCSH) ") for label in labels
    ]

    subjects = []
    for label in labels:
        nested_concept = build_concept(label, "Concept")
        subjects.append(
            Subject(
                id=nested_concept.id,
                label=nested_concept.label,
                concepts=[nested_concept],
            )
        )
    return subjects
