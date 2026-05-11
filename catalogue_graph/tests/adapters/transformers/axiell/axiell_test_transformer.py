from __future__ import annotations

from datetime import datetime

from pymarc.record import Record

from adapters.transformers.axiell_transformer import AxiellTransformer
from core.transformer import ElasticBaseTransformer as BaseTransformer
from models.pipeline.identifier import Id
from models.pipeline.source.work import InvisibleSourceWork

DEFAULT_SOURCE_MODIFIED_TIME = datetime(2020, 1, 1)


class AxiellTransformerForTests(AxiellTransformer):
    """A test-friendly Axiell transformer that exercises the real transform_record().

    We deliberately bypass the production __init__ (which requires an AdapterStore)
    while keeping the production transform_record implementation.

    Note: This class is only suitable for calling transform_record(); it doesn't
    have a configured AdapterStoreSource and should not be used with .transform().
    """

    def __init__(self) -> None:
        BaseTransformer.__init__(self)
        self.identifier_type = Id(id="axiell-priref")


def transform_axiell_record(
    marc_record: Record,
    *,
    source_modified_time: datetime = DEFAULT_SOURCE_MODIFIED_TIME,
) -> InvisibleSourceWork:
    """Convenience helper for tests."""
    transformer = AxiellTransformerForTests()
    return transformer.transform_record(
        marc_record, source_modified_time=source_modified_time
    )
