from pymarc.record import Record

from adapters.transformers.ebsco.label_subdivisions import build_concept
from adapters.transformers.marc.common import non_empty_subfields
from models.pipeline.concept import Subject


def extract_subject_labels(record: Record) -> list[str]:
    return non_empty_subfields("653", "a", record)


def extract_subjects(record: Record) -> list[Subject]:
    labels = extract_subject_labels(record)

    # Some subject labels coming from Axiell/CALM have a leading <p> prefix to indicate that the subject is linked
    # to a Library of Congress entry. Remove the prefix.
    labels = [label.removeprefix("<p>") for label in labels]

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
