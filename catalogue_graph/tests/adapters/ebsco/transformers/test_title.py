"""Deprecated.

These tests were originally EBSCO-specific and used a now-removed
`adapters.ebsco.transformers.ebsco_to_weco.transform_record`.

The equivalent coverage lives in `catalogue_graph/tests/adapters/marc/test_title.py`,
which exercises the same MARC field transformer logic via a MarcXmlTransformer
subclass designed for unit-testing MARC extractors.
"""

import pytest


pytest.skip(
    "Moved to catalogue_graph/tests/adapters/marc/test_title.py",
    allow_module_level=True,
)
