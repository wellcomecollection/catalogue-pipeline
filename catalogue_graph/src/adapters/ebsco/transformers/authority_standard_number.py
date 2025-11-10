from pymarc.field import Field
from models.pipeline.identifier import Identifiable, SourceIdentifier
from models.pipeline.id_label import Id


def _get_id_type(field: Field) -> str | None:
    return {
        "2": "nlm-mesh"
    }.get(field.indicators.second)


def extract_identifier(field: Field, ontology_type: str) -> Identifiable | None:
    if (identifier_type := _get_id_type(field)) and (identifier_subfield_value := field.get("0")):
        return Identifiable.from_source_identifier(SourceIdentifier(
            identifier_type=Id(id=identifier_type),
            ontology_type=ontology_type,
            value=identifier_subfield_value.removeprefix("https://id.nlm.nih.gov/mesh/").removeprefix("(DNLM)"),
        ))
    return None
