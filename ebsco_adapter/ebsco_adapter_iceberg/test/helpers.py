"""
Common helper functions for tests.
"""

from typing import Dict, Any, List, Optional, Collection
import pyarrow as pa

from main import data_to_pa_table, EBSCO_NAMESPACE


def add_namespace(
    data_dict: Dict[str, Any], namespace: Optional[str] = None
) -> Dict[str, Any]:
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
    unqualified_data: List[Dict[str, Any]], namespace: Optional[str] = None
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
    return data_to_pa_table(
        [add_namespace(entry.copy(), namespace) for entry in unqualified_data]
    )


def assert_row_identifiers(rows: pa.Table, expected_ids: Collection[str]) -> None:
    """
    Assert that the given rows contain exactly the expected IDs.

    Args:
        rows: PyArrow table containing rows with an 'id' column
        expected_ids: Set or collection of expected ID values
    """
    actual_ids = set(rows.column("id").to_pylist())
    assert actual_ids == set(expected_ids)
