"""
Common helper functions for tests.
"""

from collections.abc import Collection, Mapping
from datetime import UTC, datetime
from typing import Any

import pyarrow as pa
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.ebsco.marcxml_loader import MarcXmlFileLoader
from adapters.ebsco.steps.loader import EBSCO_NAMESPACE
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA


def lone_element(list_of_one: list) -> Any:
    assert len(list_of_one) == 1
    return list_of_one[0]


def add_namespace(
    data_dict: dict[str, Any], namespace: str | None = None
) -> dict[str, Any]:
    """
    Add a namespace field to a data dictionary.

    Args:
        data_dict: Dictionary containing record data
        namespace: Namespace to add (defaults to EBSCO_NAMESPACE from main)

    Returns:
        Dictionary with namespace field added
    """
    if namespace is None:
        namespace = EBSCO_NAMESPACE
    data_dict["namespace"] = namespace
    return data_dict


def data_to_namespaced_table(
    unqualified_data: list[dict[str, Any]],
    namespace: str | None = None,
    *,
    add_timestamp: bool = False,
) -> pa.Table:
    """
    Convert a list of data dictionaries to a PyArrow table with namespace added.

    Args:
        unqualified_data: List of dictionaries containing record data
        namespace: Namespace to add to all records (defaults to EBSCO_NAMESPACE from main)

    Returns:
        PyArrow table with namespace field added to all records
    """
    if namespace is None:
        namespace = EBSCO_NAMESPACE

    rows = [add_namespace(entry.copy(), namespace) for entry in unqualified_data]

    if add_timestamp:
        now = datetime.now(UTC)
        for row in rows:
            row.setdefault("last_modified", now)

    file_loader = MarcXmlFileLoader(schema=ARROW_SCHEMA, namespace=namespace)

    return file_loader.data_to_pa_table(rows)


def assert_row_identifiers(rows: pa.Table, expected_ids: Collection[str]) -> None:
    """
    Assert that the given rows contain exactly the expected IDs.

    Args:
        rows: PyArrow table containing rows with an 'id' column
        expected_ids: Set or collection of expected ID values
    """
    actual_ids = set(rows.column("id").to_pylist())
    assert actual_ids == set(expected_ids)


def prepare_changeset(
    temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
    records_by_id: Mapping[str, tuple[str, bool] | str | None],
    *,
    namespace: str,
    build_adapter_table_path: str,
) -> str:
    """Insert XML records into the temporary Iceberg table.

    Args:
        temporary_table: The temporary Iceberg table to insert into.
        monkeypatch: pytest monkeypatch fixture.
        records_by_id: Mapping of id -> content. Content can be:
            - str: visible record with that XML content
            - (str, True): deleted record with that XML content preserved
            - None: legacy format, treated as error (no content)
        namespace: The namespace for the records (e.g., EBSCO_NAMESPACE, AXIELL_NAMESPACE).
        build_adapter_table_path: The module path to monkeypatch for build_adapter_table.

    Returns the new changeset_id.
    """
    rows = []
    for rid, data in records_by_id.items():
        if isinstance(data, tuple):
            content: str | None = data[0]
            deleted = data[1]
        else:
            content = data
            deleted = False
        rows.append(
            {
                "id": rid,
                "content": content,
                "deleted": deleted,
                "last_modified": datetime.now(UTC),
            }
        )
    pa_table_initial = data_to_namespaced_table(
        rows, namespace=namespace, add_timestamp=True
    )

    client = AdapterStore(temporary_table)

    store_update = client.incremental_update(pa_table_initial, namespace)
    assert store_update is not None
    changeset_id = store_update.changeset_id

    assert changeset_id, "Expected a changeset_id to be returned"

    # Ensure transformer uses our temporary table
    monkeypatch.setattr(
        build_adapter_table_path,
        lambda use_rest_api_table, create_if_not_exists: temporary_table,
    )
    return changeset_id
