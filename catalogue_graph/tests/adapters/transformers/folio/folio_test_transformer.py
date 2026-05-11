from __future__ import annotations

from datetime import datetime

from pymarc.record import Record

from adapters.transformers.folio_transformer import FolioTransformer
from core.transformer import ElasticBaseTransformer as BaseTransformer
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork

DEFAULT_SOURCE_MODIFIED_TIME = datetime(2020, 1, 1)


class FolioTransformerForTests(FolioTransformer):
    """A test-friendly Folio transformer that exercises the real transform_record().

    Bypasses the production __init__ (which requires an AdapterStore)
    while keeping the production transform_record implementation.
    """

    def __init__(self) -> None:
        BaseTransformer.__init__(self)
        self.identifier_type = Id(id="folio-instance")
        self.predecessor_identifier_type = Id(id="sierra-system-number")


def transform_folio_record(
    marc_record: Record,
    *,
    source_modified_time: datetime = DEFAULT_SOURCE_MODIFIED_TIME,
) -> VisibleSourceWork:
    """Convenience helper for tests."""
    transformer = FolioTransformerForTests()
    return transformer.transform_record(
        marc_record, source_modified_time=source_modified_time
    )
