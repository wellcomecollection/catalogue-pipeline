"""
Other identifiers specific to FOLIO records.

- 759: Wellcome digcodes, a local field (not assigned by MARC 21) naming the
  digitisation project an item was digitised under, e.g. `digmoh` for the MOH reports.
"""

import re

from pymarc.record import Record

from adapters.transformers.marc.common import all_non_empty_subfields, distinct
from models import identifier_schemes
from models.pipeline.identifier import Id, SourceIdentifier, WorkSourceIdentifier

# Only the alphabetic run is the digcode: `digmoh(Channel)` is `digmoh`.
DIGCODE_RE = re.compile(r"dig[a-z]+")


def extract_digcodes(record: Record) -> list[SourceIdentifier]:
    matches = (
        DIGCODE_RE.match(value) for value in all_non_empty_subfields("759", "a", record)
    )
    return [
        WorkSourceIdentifier(
            identifier_type=Id(id=identifier_schemes.WELLCOME_DIGCODE.id), value=code
        )
        for code in distinct(match.group() for match in matches if match)
    ]
