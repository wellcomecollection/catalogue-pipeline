"""The series relations extracted from MARC are set on the work state.

The extraction rules themselves are covered by the parents feature in the
FOLIO BDD suite.
"""

from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from models.pipeline.work_state import WorkAncestor, WorkRelations
from tests.adapters.transformers.marc.marcxml_test_transformer import (
    MarcXmlWorkBuilderForTests,
)


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="440",
                    subfields=[Subfield(code="a", value="A title from 440ǂa")],
                ),
            ]
        )
    ],
    indirect=["marc_record"],
)
def test_relations_are_set_from_parents(marc_record: Record) -> None:
    transformer = MarcXmlWorkBuilderForTests(marc_record, last_modified=datetime.now())

    assert transformer.visible_work_state.relations == WorkRelations(
        ancestors=[
            WorkAncestor(
                title="A title from 440ǂa",
                work_type="Series",
                depth=0,
                num_children=0,
                num_descendents=0,
            )
        ]
    )
