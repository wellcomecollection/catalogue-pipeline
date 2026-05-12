from core.transformer import ElasticBaseTransformer
from models.pipeline.identifier import Id, SourceIdentifier
from models.pipeline.serialisable import SerialisableModel


class StubDocument(SerialisableModel):
    source_identifier: SourceIdentifier
    predecessor_identifier: SourceIdentifier | None = None


def test_generate_bulk_load_actions_excludes_none_fields() -> None:
    """Null fields (e.g. predecessorIdentifier) must not appear in _source."""

    class StubTransformer(ElasticBaseTransformer[StubDocument]):
        def _get_document_id(self, record: StubDocument) -> str:
            return str(record.source_identifier)

    transformer = StubTransformer()
    doc = StubDocument(
        source_identifier=SourceIdentifier(
            identifier_type=Id(id="test-type"),
            ontology_type="Work",
            value="abc123",
        ),
        predecessor_identifier=None,
    )

    actions = list(transformer._generate_bulk_load_actions([doc], "test-index"))

    assert len(actions) == 1
    source = actions[0]["_source"]
    assert "predecessorIdentifier" not in source
    assert "sourceIdentifier" in source
