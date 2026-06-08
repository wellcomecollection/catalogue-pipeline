from __future__ import annotations

from adapters.transformers.marcxml_record_transformer import MarcXMLRecordTransformer
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from models.pipeline.id_label import Id


class MarcXmlRecordTransformerForTests(MarcXMLRecordTransformer):
    @property
    def source_identifier_type(self) -> Id:
        return Id(id="marc-test")


class MarcXmlTransformerForTests(MarcXmlTransformer):
    @property
    def record_transformer(self) -> type[MarcXMLRecordTransformer]:
        return MarcXmlRecordTransformerForTests
