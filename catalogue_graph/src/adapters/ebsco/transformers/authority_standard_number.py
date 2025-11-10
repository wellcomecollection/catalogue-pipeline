from pymarc.field import Field

from models.pipeline.id_label import Id
from models.pipeline.identifier import Identifiable, SourceIdentifier


def _get_id_type(field: Field) -> str | None:
    return {"2": "nlm-mesh"}.get(field.indicators.second)


def extract_identifier(field: Field, ontology_type: str) -> Identifiable | None:
    if (identifier_type := _get_id_type(field)) and (
        identifier_subfield_value := field.get("0")
    ):
        return Identifiable.from_source_identifier(
            SourceIdentifier(
                identifier_type=Id(id=identifier_type),
                ontology_type=ontology_type,
                # currently only MeSH uses this path, implement a way to select different
                # normalisations when we add another authority.
                value=normalise_mesh_id(identifier_subfield_value),
            )
        )
    return None


def normalise_mesh_id(value: str) -> str:
    return value.removeprefix("https://id.nlm.nih.gov/mesh/").removeprefix("(DNLM)")
