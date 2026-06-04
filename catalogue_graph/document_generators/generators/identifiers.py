from models.pipeline.id_label import Id
from models.pipeline.identifier import Identified, SourceIdentifier

from .random import random_alphanumeric, random_canonical_id


def create_source_identifier(
    ontology_type: str = "Work",
    identifier_type_id: str = "sierra-system-number",
) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=Id(id=identifier_type_id),
        ontology_type=ontology_type,
        value=random_alphanumeric(10),
    )


def create_identified(
    canonical_id: str | None = None, ontology_type: str = "Work"
) -> Identified:
    return Identified(
        canonical_id=canonical_id or random_canonical_id(),
        source_identifier=create_source_identifier(ontology_type=ontology_type),
    )
