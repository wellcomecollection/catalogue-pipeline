from __future__ import annotations

from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from models.pipeline.id_label import Id


class MarcXmlWorkBuilderForTests(MarcXmlWorkBuilder):
    @property
    def source_identifier_type(self) -> Id:
        return Id(id="marc-test")


class MarcXmlTransformerForTests(MarcXmlTransformer):
    @property
    def work_builder(self) -> type[MarcXmlWorkBuilder]:
        return MarcXmlWorkBuilderForTests
