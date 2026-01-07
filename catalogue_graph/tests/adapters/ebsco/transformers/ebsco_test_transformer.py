from __future__ import annotations

from datetime import datetime

from pymarc.record import Record

from adapters.transformers.base_transformer import BaseTransformer
from adapters.transformers.ebsco_transformer import EbscoTransformer
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork

DEFAULT_SOURCE_MODIFIED_TIME = datetime(2020, 1, 1)


class EbscoTransformerForTests(EbscoTransformer):
    """A test-friendly EBSCO transformer that exercises the real transform_record().

    We deliberately bypass the production __init__ (which requires an AdapterStore)
    while keeping the production transform_record implementation.

    Note: This class is only suitable for calling transform_record(); it doesn't
    have a configured AdapterStoreSource and should not be used with .transform().
    """

    def __init__(self) -> None:
        BaseTransformer.__init__(self)
        # Used by MarcXmlTransformer.source_identifier() when building SourceWorkState
        self.identifier_type = Id(id="ebsco-alt-lookup")


def transform_ebsco_record(
    marc_record: Record,
    *,
    source_modified_time: datetime = DEFAULT_SOURCE_MODIFIED_TIME,
) -> VisibleSourceWork:
    """Convenience helper for tests."""

    return EbscoTransformerForTests().transform_record(
        marc_record, source_modified_time=source_modified_time
    )
