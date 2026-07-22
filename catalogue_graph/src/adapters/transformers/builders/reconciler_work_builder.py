from adapters.transformers.builders.source_work_builder import SourceWorkBuilder
from models.pipeline.identifier import Id


class ReconcilerWorkBuilder(SourceWorkBuilder):
    """
    Used by `AxiellTransformer` to emit deleted works from deletion facts.

    Operates on a raw GUID string rather than a MARC record (facts only carry
    the resolved GUID).
    """

    @property
    def source_identifier_type(self) -> Id:
        return Id(id="axiell-guid")
