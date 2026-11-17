"""The generated mapping CSVs in docs/axiell-folio-mappings match config.py.

A table changed without regenerating fails here. Regenerate with
``uv run python -m adapters.steps.axiell_folio_sync.mapping.csv_export``.
"""

from __future__ import annotations

import pytest

from adapters.steps.axiell_folio_sync.mapping import VERSION
from adapters.steps.axiell_folio_sync.mapping.csv_export import (
    DEFAULT_DIRECTORY,
    render,
)


@pytest.mark.parametrize("name", sorted(render()))
def test_the_generated_file_is_up_to_date(name: str) -> None:
    on_disk = (DEFAULT_DIRECTORY / name).read_text()

    assert on_disk == render()[name], (
        f"{name} is out of date: regenerate it with"
        " `uv run python -m adapters.steps.axiell_folio_sync.mapping.csv_export`"
    )


def test_the_readme_records_the_mapping_version() -> None:
    assert f"Mapping version {VERSION} " in render()["README.md"]


def test_the_folder_holds_only_the_generated_csvs() -> None:
    on_disk = {path.name for path in DEFAULT_DIRECTORY.glob("*.csv")}

    assert on_disk == {name for name in render() if name.endswith(".csv")}
