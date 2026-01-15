from __future__ import annotations

from datetime import datetime

from pymarc.record import Record

from adapters.transformers.axiell_transformer import AxiellTransformer
from adapters.transformers.base_transformer import BaseTransformer
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

    def transform_marc_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> InvisibleSourceWork:
        """Convenience method for tests that extracts work_id and calls transform_record."""
        work_id = self.extract_work_id(marc_record)
        return self.transform_record(work_id, marc_record, source_modified_time)


def transform_axiell_record(
    marc_record: Record,
    *,
    source_modified_time: datetime = DEFAULT_SOURCE_MODIFIED_TIME,
) -> InvisibleSourceWork:
    """Convenience helper for tests."""

    return AxiellTransformerForTests().transform_marc_record(
        marc_record, source_modified_time=source_modified_time
    )
