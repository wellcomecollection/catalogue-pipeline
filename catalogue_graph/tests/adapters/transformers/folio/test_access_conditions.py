from dataclasses import fields

import pytest

from adapters.transformers.folio.access_conditions import (
    INPUT_COLUMNS,
    FolioItemData,
    notes,
    rules,
)
from models.pipeline.access_method import AccessMethod
from models.pipeline.access_status import AccessStatus


def test_rule_ids_are_unique() -> None:
    ids = [r["rule_id"] for r in rules()]
    assert len(ids) == len(set(ids))


def test_table_ends_in_a_catch_all() -> None:
    assert all(not rules()[-1][column] for column in INPUT_COLUMNS)


@pytest.mark.parametrize("rule", rules(), ids=lambda r: r["rule_id"])
def test_every_row_builds_a_valid_condition(rule: dict[str, str]) -> None:
    AccessMethod(type=rule["method"])  # type: ignore[arg-type]
    if rule["status"]:
        AccessStatus(type=rule["status"])  # type: ignore[arg-type]
    assert rule["note"] in {"", "exhibition", *notes()}
    assert rule["display_note"] in {
        "condition-note-wins",
        "display-note-wins-if-manual-request",
    }


def test_every_input_column_is_a_field_or_the_open_request() -> None:
    # Matching reads fields by column name, so a renamed field would stop matching silently.
    assert set(rules()[0]) >= set(INPUT_COLUMNS)
    assert set(INPUT_COLUMNS) - {f.name for f in fields(FolioItemData)} == {
        "open_request"
    }
