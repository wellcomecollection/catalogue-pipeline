from adapters.transformers.builders.source_work_builder import SourceWorkBuilder
from models.pipeline.identifier import Id


class ReconcilerWorkBuilder(SourceWorkBuilder):
    @property
    def source_identifier_type(self) -> Id:
        return Id(id="axiell-guid")
