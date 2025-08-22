import pymarc

from models.work import ElasticsearchModel


# TODO: This is from catalogue_graph.ingestor.models.
# import it rather than copy
class SourceIdentifier(ElasticsearchModel):
    identifier_type: str
    ontology_type: str
    value: str


# TODO: Move this to a file in models when it's finished
class Work(ElasticsearchModel):
    source_identifier: SourceIdentifier
    title: str


def ebsco_source_identifier(id_value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type="ebsco-alt-lookup", ontology_type="Work", value=id_value
    )


def transform(marc_record: pymarc.record.Record) -> Work:
    id_field = marc_record.get("001")
    assert id_field is not None
    title = marc_record.title
    assert title is not None
    return Work(
        source_identifier=ebsco_source_identifier(id_field.format_field()),
        title=title,
    )
