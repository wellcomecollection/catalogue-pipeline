from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ElasticsearchModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
        serialize_by_alias=True,
    )


class SourceIdentifier(ElasticsearchModel):
    identifier_type: str  # e.g. 'ebsco-alt-lookup'
    ontology_type: str  # always 'Work' for this adapter
    value: str  # raw source ID

    def __str__(self) -> str:
        return f"Work[{self.identifier_type}/{self.value}]"


class BaseWork(BaseModel):
    source_identifier: SourceIdentifier


class DeletedWork(ElasticsearchModel, BaseWork):
    deleted_reason: str


class SourceWork(ElasticsearchModel, BaseWork):
    title: str
