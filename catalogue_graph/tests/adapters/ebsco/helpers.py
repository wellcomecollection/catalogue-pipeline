"""
Common helper functions for tests.
"""

from collections.abc import Collection
from datetime import UTC, datetime
from typing import Any

import pyarrow as pa

from adapters.ebsco.marcxml_loader import MarcXmlFileLoader
from adapters.ebsco.steps.loader import EBSCO_NAMESPACE
from adapters.utils.schemata import ARROW_SCHEMA_WITH_TIMESTAMP

file_loader = MarcXmlFileLoader(
    schema=ARROW_SCHEMA_WITH_TIMESTAMP, namespace=EBSCO_NAMESPACE
)


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
            row["last_modified"] = now

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
