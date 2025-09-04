from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ElasticsearchModel(BaseModel):
    # This model config automatically converts between snake_case and camelCase when validating and serialising.
    # Instances of this model (and its subclasses) can be constructed using both snake_case and camelCase properties,
    # allowing us to continue using snake_case in Python code and camelCase in Elasticsearch documents.
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
        serialize_by_alias=True,
    )


class BaseWork(BaseModel):
    """
    Base class for work models, providing common attributes and methods.
    """

    id: str


class DeletedWork(ElasticsearchModel, BaseWork):
    deleted_reason: str


# TODO: This is from catalogue_graph.ingestor.models.
# import it rather than copy
class SourceIdentifier(ElasticsearchModel):
    identifier_type: str
    ontology_type: str
    value: str


class SourceWork(ElasticsearchModel, BaseWork):
    title: str
    alternative_titles: list[str] = []
    other_identifiers: list[SourceIdentifier] = []
    designation: list[str] = []
    description: str | None = None
    current_frequency: str | None = None
    edition: str | None = None
